package com.captechomar.widgets.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withLink
import androidx.core.content.ContextCompat
import com.captechomar.widgets.data.Place
import com.captechomar.widgets.data.WeatherConfig
import com.captechomar.widgets.data.WeatherStore
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.Palettes
import com.captechomar.widgets.widgets.WeatherRefresh
import com.captechomar.widgets.widgets.WeatherRenderer
import com.captechomar.widgets.widgets.WidgetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Settings of a weather widget: the place, units and colours. */
@Composable
fun WeatherConfigPanel(widgetId: Int, kind: WidgetKind, saveLabel: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(WidgetPrefs.load(context, widgetId, kind.defaultConfig)) }
    val w = cfg.weather
    fun update(change: (WeatherConfig) -> WeatherConfig) {
        cfg = cfg.copy(weather = change(cfg.weather))
    }

    var version by remember { mutableStateOf(0) }
    var permissionTick by remember { mutableStateOf(0) }
    val backgroundLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick++
        // rebind the widget so its icon tap matches the new access
        com.captechomar.widgets.widgets.WidgetUpdater.updateKind(context, com.captechomar.widgets.widgets.WeatherKind)
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    /** Fetches the weather for the chosen place right away so the preview shows it. */
    fun load(weather: WeatherConfig) {
        scope.launch {
            val updated = withContext(Dispatchers.IO) { WeatherStore.refresh(context, weather) }
            cfg = cfg.copy(weather = cfg.weather.copy(city = updated.city, lat = updated.lat, lon = updated.lon, hasPlace = updated.hasPlace))
            version++
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val next = cfg.weather.copy(useLocation = true)
            update { next }
            message = ""
            load(next)
        } else {
            message = "Location permission was not granted."
            update { it.copy(useLocation = false) }
        }
    }

    val preview = remember(cfg, version) { kind.render(context, cfg, 900, (900 * WeatherRenderer.REF_H / WeatherRenderer.REF_W).roundToInt()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // the clock is a live view on the real widget; here it is drawn over the same tile
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(WeatherRenderer.REF_W / WeatherRenderer.REF_H)) {
            Image(bitmap = preview.asImageBitmap(), contentDescription = "Widget preview", modifier = Modifier.fillMaxSize())
            val k = maxWidth / WeatherRenderer.REF_W
            val tile = WeatherRenderer.TILE
            val ink = androidx.compose.ui.graphics.Color(Palettes.resolve(context, cfg.palette).text)
            val now = remember { Date() }
            Column(
                Modifier.offset(k * tile.left, k * tile.top).size(k * (tile.right - tile.left), k * (tile.bottom - tile.top)).padding(bottom = k * 60),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                with(LocalDensity.current) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = ink, fontSize = (k * 98).toSp(), style = MaterialTheme.typography.titleLarge.copy(fontSize = (k * 98).toSp(), lineHeight = (k * 106).toSp()))
                }
            }
        }

        Section("Colors") {
            PaletteRow(cfg.palette) { cfg = cfg.copy(palette = it) }
            NumberSlider("Roundness", (cfg.roundness * 100).roundToInt(), 0..100) { cfg = cfg.copy(roundness = it / 100f) }
        }

        Section("Location") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search for a city") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = query.isNotBlank() && !searching,
                    onClick = {
                        searching = true
                        message = ""
                        scope.launch {
                            results = withContext(Dispatchers.IO) { WeatherStore.search(query) }
                            if (results.isEmpty()) message = "No city found. Check the spelling or your connection."
                            searching = false
                        }
                    },
                ) { Text("Search") }
            }
            results.forEach { p ->
                Column(
                    Modifier.fillMaxWidth().clickable {
                        val next = cfg.weather.copy(city = p.name, lat = p.lat, lon = p.lon, hasPlace = true, useLocation = false)
                        update { next }
                        results = emptyList()
                        query = ""
                        load(next)
                    }.padding(vertical = 6.dp)
                ) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    if (p.detail.isNotBlank()) Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(
                if (w.hasPlace) "Showing ${w.city.ifBlank { "your location" }}" else "No city picked yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow("Use my location", "Follow this phone instead of a fixed city", w.useLocation) { on ->
                if (!on) update { it.copy(useLocation = false) }
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val next = cfg.weather.copy(useLocation = true)
                    update { next }
                    load(next)
                } else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (w.useLocation) {
                val allTheTime = remember(permissionTick) {
                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                }
                val link = androidx.compose.ui.text.TextLinkStyles(
                    androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                )
                Text(
                    if (allTheTime) androidx.compose.ui.text.AnnotatedString("The location updates in the background each hour and tap the weather icon for quick update.")
                    else androidx.compose.ui.text.buildAnnotatedString {
                        append("Tap the weather icon to update the location, or ")
                        withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("background", link) {
                            backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }) { append("give background permission") }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ToggleRow("Fahrenheit and mph", "Show °F and miles per hour", w.imperial) { on -> update { it.copy(imperial = on) } }
        }

        Section("Taps") {
            Text("Widget", style = MaterialTheme.typography.titleSmall)
            TapActionEditor(w.widgetTap, { t -> update { it.copy(widgetTap = t) } }, TapPick.WIDGET)
            Text("Time", style = MaterialTheme.typography.titleSmall)
            TapActionEditor(w.clockTap, { t -> update { it.copy(clockTap = t) } }, TapPick.CLOCK)
            Text("Date", style = MaterialTheme.typography.titleSmall)
            TapActionEditor(w.dateTap, { t -> update { it.copy(dateTap = t) } }, TapPick.CALENDAR)
            Text(
                "Tapping the weather icon refreshes the weather and, if \"Use my location\" is on, your location.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = {
                WidgetPrefs.save(context, widgetId, cfg)
                WeatherRefresh.enqueue(context)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(saveLabel) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
