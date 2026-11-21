package com.captechomar.widgets.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.DrawingStore
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.WidgetKind
import kotlin.math.roundToInt

/** Settings of a drawing widget: its colours, and a way to wipe the drawing. */
@Composable
fun DrawingConfigPanel(widgetId: Int, kind: WidgetKind, saveLabel: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(WidgetPrefs.load(context, widgetId, kind.defaultConfig)) }
    var version by remember { mutableStateOf(0) }
    val preview = remember(cfg, version) { kind.render(context, cfg, 900, 600) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(
            bitmap = preview.asImageBitmap(), contentDescription = "Widget preview",
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
        )
        Section("Colors") {
            PaletteRow(cfg.palette) { cfg = cfg.copy(palette = it) }
            NumberSlider("Roundness", (cfg.roundness * 100).roundToInt(), 0..100) { cfg = cfg.copy(roundness = it / 100f) }
        }
        Section("Drawing") {
            Text("Tap the widget to draw on it. Long-press the widget to come back here.")
            OutlinedButton(onClick = { DrawingStore.delete(context, widgetId); version++ }) { Text("Clear drawing") }
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
