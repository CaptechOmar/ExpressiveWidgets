package com.captechomar.widgets.ui

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.BatteryReader
import com.captechomar.widgets.data.WidgetConfig
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.BatteryRenderer
import com.captechomar.widgets.widgets.Palettes

/** Settings for one battery widget with a live preview. Shared by the widget-configure screen and the main app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigPanel(widgetId: Int, saveLabel: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(WidgetPrefs.load(context, widgetId)) }
    var hasPermission by remember { mutableStateOf(BatteryReader.hasBluetoothPermission(context)) }
    var devices by remember { mutableStateOf(BatteryReader.pairedDevices(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        devices = BatteryReader.pairedDevices(context)
    }

    val preview = remember(cfg, hasPermission) { BatteryRenderer.render(context, cfg, 900, 230) }

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

        Text("Roundness", style = MaterialTheme.typography.titleMedium)
        Slider(value = cfg.roundness, onValueChange = { cfg = cfg.copy(roundness = it) })

        Text("Earphones", style = MaterialTheme.typography.titleMedium)
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

        Button(
            onClick = {
                WidgetPrefs.save(context, widgetId, cfg)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(saveLabel) }
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
