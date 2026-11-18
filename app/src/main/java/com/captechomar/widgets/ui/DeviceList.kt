package com.captechomar.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.captechomar.widgets.R
import com.captechomar.widgets.data.DeviceKind
import com.captechomar.widgets.data.IconChoice
import com.captechomar.widgets.data.TapAction
import com.captechomar.widgets.data.WidgetConfig
import com.captechomar.widgets.widgets.DeviceIcons

/** A device the user can put on the widget. [key] is its Bluetooth address, or a "gb:" name for Gadgetbridge. */
data class DeviceEntry(val key: String, val name: String, val kind: DeviceKind)

/**
 * The battery widget's blocks: the phone, then every detected device with a drag handle, a settings gear and a toggle.
 * The enabled devices, in list order, become the bars next to the phone.
 */
@Composable
fun DeviceList(cfg: WidgetConfig, entries: List<DeviceEntry>, autoEnabled: Set<String>, onChange: (WidgetConfig) -> Unit) {
    val byKey = entries.associateBy { it.key }
    val order = (cfg.deviceOrder + cfg.deviceAddresses + entries.map { it.key }).distinct().filter { it in byKey }
    val enabled = if (!cfg.manualDevices && cfg.deviceAddresses.isEmpty()) autoEnabled else cfg.deviceAddresses.toSet()

    val commit = { newOrder: List<String>, on: Set<String> ->
        onChange(cfg.copy(manualDevices = true, deviceOrder = newOrder, deviceAddresses = newOrder.filter { it in on }))
    }
    val commitNow by rememberUpdatedState(commit)
    val orderNow by rememberUpdatedState(order)
    val enabledNow by rememberUpdatedState(enabled)

    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    var settingsFor by remember { mutableStateOf<String?>(null) }

    Column {
        // the phone is always first
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(36.dp))
            Text("Phone", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { settingsFor = TapAction.PHONE }) {
                Icon(painterResource(R.drawable.ms_settings), contentDescription = "Phone settings")
            }
            Spacer(Modifier.width(60.dp))
        }

        order.forEach { key ->
            androidx.compose.runtime.key(key) {
                val entry = byKey.getValue(key)
                val on = key in enabled
                val dragging = dragKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .onSizeChanged { rowHeight = it.height.toFloat().coerceAtLeast(1f) }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_drag_handle),
                        contentDescription = "Drag to reorder",
                        modifier = Modifier
                            .size(36.dp)
                            .padding(6.dp)
                            .pointerInput(key) {
                                detectDragGestures(
                                    onDragStart = { dragKey = key; dragOffset = 0f },
                                    onDragEnd = { dragKey = null; dragOffset = 0f },
                                    onDragCancel = { dragKey = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val list = orderNow
                                    val i = list.indexOf(key)
                                    val j = when {
                                        dragOffset > rowHeight / 2 && i < list.lastIndex -> i + 1
                                        dragOffset < -rowHeight / 2 && i > 0 -> i - 1
                                        else -> -1
                                    }
                                    if (j >= 0) {
                                        val moved = list.toMutableList().also { it.add(j, it.removeAt(i)) }
                                        dragOffset += if (j > i) -rowHeight else rowHeight
                                        commitNow(moved, enabledNow)
                                    }
                                }
                            },
                    )
                    Text(entry.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { settingsFor = key }) {
                        Icon(painterResource(R.drawable.ms_settings), contentDescription = "${entry.name} settings")
                    }
                    Switch(
                        checked = on,
                        onCheckedChange = { checked -> commit(order, if (checked) enabled + key else enabled - key) },
                        thumbContent = if (on) {
                            { Icon(painterResource(R.drawable.ms_check), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                        } else null,
                    )
                }
            }
        }
        if (order.isEmpty()) {
            Text("No devices found yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }

    val open = settingsFor
    if (open != null) {
        val entry = byKey[open]
        DeviceSettingsDialog(
            title = if (open == TapAction.PHONE) "Phone" else entry?.name ?: open,
            choice = cfg.icons[open] ?: DeviceIcons.defaultChoice(open, entry?.kind ?: DeviceKind.OTHER),
            action = cfg.tapActions[open] ?: TapAction(),
            onIcon = { onChange(cfg.copy(icons = cfg.icons + (open to it))) },
            onAction = { onChange(cfg.copy(tapActions = cfg.tapActions + (open to it))) },
            onDismiss = { settingsFor = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceSettingsDialog(
    title: String,
    choice: IconChoice,
    action: TapAction,
    onIcon: (IconChoice) -> Unit,
    onAction: (TapAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = DeviceIcons.entry(choice.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Icon", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceIcons.all.forEach { entry ->
                        val selected = entry.id == choice.id
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { onIcon(choice.copy(id = entry.id)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(DeviceIcons.res(IconChoice(entry.id, choice.filled))),
                                contentDescription = entry.label,
                                modifier = Modifier.size(28.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Filled", style = MaterialTheme.typography.bodyLarge)
                        if (current.filled == null) {
                            Text("This icon has no filled version.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = choice.filled && current.filled != null,
                        enabled = current.filled != null,
                        onCheckedChange = { onIcon(choice.copy(filled = it)) },
                    )
                }
                Text("When tapped", style = MaterialTheme.typography.titleMedium)
                TapActionEditor(action, onAction)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
