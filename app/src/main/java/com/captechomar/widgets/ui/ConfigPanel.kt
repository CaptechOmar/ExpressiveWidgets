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
import com.captechomar.widgets.data.GadgetbridgeStore
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

    var notificationAccess by remember { mutableStateOf(MediaAccess.isEnabled(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notificationAccess = MediaAccess.isEnabled(context)
    }

    val preview = remember(cfg, hasPermission, notificationAccess) { kind.render(context, cfg, 900, 230) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "Widget preview",
            modifier = Modifier.fillMaxWidth().aspectRatio(900f / 230f),
        )

        Text("Colors", style = MaterialTheme.typography.titleMedium)
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
                Text("Colors from album art", style = MaterialTheme.typography.titleMedium)
                Switch(checked = cfg.artColors, onCheckedChange = { cfg = cfg.copy(artColors = it) })
            }
        }

        // Shape settings are whole numbers so the same shape can be typed in on another widget.
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

        if (kind.usesEarphones) {
            Text("Bluetooth devices", style = MaterialTheme.typography.titleMedium)
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text(
                    "Allow Bluetooth access so the widget can read your earphones' battery.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                    Text("Allow Bluetooth access")
                }
            } else {
                DeviceRow("Automatic (first connected audio device)", cfg.deviceAddress == null) {
                    cfg = cfg.copy(deviceAddress = null)
                }
                devices.forEach { d ->
                    DeviceRow(d.name, cfg.deviceAddress == d.address) { cfg = cfg.copy(deviceAddress = d.address) }
                }
                if (devices.isEmpty()) {
                    Text("No paired Bluetooth devices found.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Gadgetbridge devices", style = MaterialTheme.typography.titleMedium)
            if (!notificationAccess) {
                Text(
                    "Gadgetbridge has no battery API, but it shows every device's battery in its always-on " +
                        "notification. Allow notification access so the widget can read it. If the switch is " +
                        "greyed out, open this app's info page, tap the three dots and choose " +
                        "\"Allow restricted settings\" first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = {
                    notificationLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Open notification access") }
            } else {
                val chosen = cfg.deviceAddress?.takeIf { GadgetbridgeStore.isGbAddress(it) }?.let { GadgetbridgeStore.nameOf(it) }
                val names = (GadgetbridgeStore.known(context) + listOfNotNull(chosen)).distinct().sorted()
                if (names.isEmpty()) {
                    Text(
                        "No Gadgetbridge devices seen yet. Connect a device in Gadgetbridge and it will appear here. " +
                            "Its battery level has to be shown in Gadgetbridge's notification.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    names.forEach { name ->
                        val address = GadgetbridgeStore.addressOf(name)
                        DeviceRow(name, cfg.deviceAddress == address) { cfg = cfg.copy(deviceAddress = address) }
                    }
                }
            }
        }

        if (kind.needsNotificationAccess && !notificationAccess) {
            Text("Notification access", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android only lets an app see what's playing through notification access. " +
                    "If the switch is greyed out, open this app's info page, tap the three dots and choose " +
                    "\"Allow restricted settings\" first.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = {
                notificationLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Open notification access") }
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

/** A slider with an editable whole-number value next to its label. */
@Composable
private fun NumberSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    // reset the text whenever the slider moves the value
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    val n = digits.toIntOrNull()
                    if (n == null) {
                        text = digits
                    } else {
                        val clamped = n.coerceIn(range)
                        text = if (clamped != n) clamped.toString() else digits
                        onChange(clamped)
                    }
                },
                modifier = Modifier.width(96.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun DeviceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
