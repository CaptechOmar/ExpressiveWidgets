package com.captechomar.widgets.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.BatteryReader
import com.captechomar.widgets.data.DeviceKind
import com.captechomar.widgets.data.GadgetbridgeStore
import com.captechomar.widgets.data.OpenScq30Store
import com.captechomar.widgets.data.TapAction
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.music.MediaAccess
import com.captechomar.widgets.widgets.Palettes
import com.captechomar.widgets.widgets.WidgetKind
import kotlin.math.roundToInt

/** Settings for one widget with a live preview. Shared by the widget-configure screen and the main app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigPanel(widgetId: Int, kind: WidgetKind, saveLabel: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(WidgetPrefs.load(context, widgetId, kind.defaultConfig)) }
    var hasPermission by remember { mutableStateOf(BatteryReader.hasBluetoothPermission(context)) }
    var devices by remember { mutableStateOf(BatteryReader.pairedDevices(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        devices = BatteryReader.pairedDevices(context)
    }

    val autoPick = remember { if (kind.usesEarphones) setOfNotNull(BatteryReader.earphones(context, null)?.address) else emptySet() }
    var notificationAccess by remember { mutableStateOf(MediaAccess.isEnabled(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notificationAccess = MediaAccess.isEnabled(context)
    }

    var artVersion by remember { mutableStateOf(0) }
    val preview = remember(cfg, hasPermission, notificationAccess, artVersion) { kind.render(context, cfg, 900, 230) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "Widget preview",
            modifier = Modifier.fillMaxWidth().aspectRatio(900f / 230f),
        )

        Section("Colors") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Palettes.options.forEach { option ->
                    FilterChip(
                        selected = cfg.palette == option.id,
                        onClick = { cfg = cfg.copy(palette = option.id) },
                        label = { Text(option.label) },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (kind.supportsArt) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Colors from album art", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = cfg.artColors, onCheckedChange = { cfg = cfg.copy(artColors = it) })
                }
            }
        }

        if (kind.supportsArt) {
            NoArtSection(cfg.noArtMode, onMode = { cfg = cfg.copy(noArtMode = it) }, onChanged = { artVersion++ })
        }

        // Shape settings are whole numbers so the same shape can be typed in on another widget.
        Section("Shape") {
            NumberSlider(
                label = if (kind.supportsArt) "Widget roundness" else "Roundness",
                value = (cfg.roundness * 100).roundToInt(),
                range = 0..100,
                onChange = { cfg = cfg.copy(roundness = it / 100f) },
            )
            if (kind.supportsArt) {
                NumberSlider(
                    label = "Album art roundness",
                    value = (cfg.artRoundness * 100).roundToInt(),
                    range = 0..100,
                    onChange = { cfg = cfg.copy(artRoundness = it / 100f) },
                )
                NumberSlider(
                    label = "Height (%)",
                    value = (cfg.heightScale * 100).roundToInt(),
                    range = 60..160,
                    onChange = { cfg = cfg.copy(heightScale = it / 100f) },
                )
            }
            Text(
                "Type a number to set a value exactly. Use the same numbers on another widget to get the same shape.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (kind.usesEarphones) {
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActionContainer(
                    "Bluetooth access needed",
                    "Lets the widget read your earphones' battery.",
                    "Allow",
                ) { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            }
            if (!notificationAccess) {
                ActionContainer(
                    "Notification access needed",
                    "Gadgetbridge and OpenSCQ30 devices are read from their notifications. If the switch is greyed out, " +
                        "open this app's info page, tap the three dots and choose \"Allow restricted settings\" first.",
                    "Open",
                ) { notificationLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            Section("Devices") {
                Text(
                    "Drag the handle to reorder. The toggle puts a device on the widget while it is connected. " +
                        "The gear changes its icon and what happens when you tap it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            val entries = ArrayList<DeviceEntry>()
            devices.forEach { entries += DeviceEntry(it.address, it.name, it.kind) }
            GadgetbridgeStore.known(context).forEach {
                entries += DeviceEntry(GadgetbridgeStore.addressOf(it), it, DeviceKind.fromName(it) ?: DeviceKind.WATCH)
            }
            OpenScq30Store.entries(context).forEach {
                entries += DeviceEntry(
                    it.address, it.label,
                    when (it.part) {
                        OpenScq30Store.Part.LEFT -> DeviceKind.EARBUD_LEFT
                        OpenScq30Store.Part.RIGHT -> DeviceKind.EARBUD_RIGHT
                        OpenScq30Store.Part.CASE -> DeviceKind.EARBUD_CASE
                        OpenScq30Store.Part.ALL -> DeviceKind.fromName(it.label) ?: DeviceKind.EARBUDS
                    },
                )
            }
            cfg.deviceAddresses.filter { a -> entries.none { it.key == a } }.forEach { a ->
                entries += DeviceEntry(a, if (GadgetbridgeStore.isGbAddress(a)) GadgetbridgeStore.nameOf(a) else if (OpenScq30Store.isAddress(a)) OpenScq30Store.labelOf(a) else a, DeviceKind.OTHER)
            }
            DeviceList(cfg, entries, autoPick) { cfg = it }
            }
        }

        if (kind.needsNotificationAccess && !notificationAccess) {
            ActionContainer(
                "Notification access needed",
                "Android only lets an app see what's playing through notification access. If the switch is greyed out, " +
                    "open this app's info page, tap the three dots and choose \"Allow restricted settings\" first.",
                "Open",
            ) { notificationLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }

        Button(
            onClick = {
                WidgetPrefs.save(context, widgetId, cfg)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(saveLabel) }
    }
}

/** A slider with its current whole-number value shown next to the label. */
@Composable
internal fun NumberSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

private fun toggled(list: List<String>, address: String): List<String> =
    if (address in list) list - address else list + address

/** A device in the picker: a checkbox for devices, a radio button for the "automatic" row. */
@Composable
private fun DeviceRow(label: String, selected: Boolean, single: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (single) RadioButton(selected = selected, onClick = null) else Checkbox(checked = selected, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
