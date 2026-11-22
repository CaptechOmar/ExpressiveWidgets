package com.captechomar.widgets.ui.tools

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.captechomar.widgets.R
import kotlin.math.roundToInt

/** How many screen pixels make a millimetre. Starts from the phone's reported density, then the user's credit-card calibration. */
object RulerCalibration {
    private const val KEY = "ruler_px_per_mm"
    const val CARD_WIDTH_MM = 53.98f // a bank card, ID-1: 85.60 x 53.98 mm
    const val CARD_HEIGHT_MM = 85.60f

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tools", Context.MODE_PRIVATE)

    fun default(context: Context): Float {
        val dm = context.resources.displayMetrics
        val dpi = if (dm.ydpi > 0f) dm.ydpi else dm.density * 160f
        return dpi / 25.4f
    }

    fun get(context: Context): Float = prefs(context).getFloat(KEY, 0f).takeIf { it > 1f } ?: default(context)
    fun save(context: Context, pxPerMm: Float) = prefs(context).edit().putFloat(KEY, pxPerMm).apply()
    fun reset(context: Context) = prefs(context).edit().remove(KEY).apply()
}

private fun Context.activity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Hides the status and navigation bars while it is on screen, so the ruler starts at the very edge. */
@Composable
fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
fun RulerTool() {
    val context = LocalContext.current
    HideSystemBars()
    var metric by rememberSaveable { mutableStateOf(true) }
    var calibrating by remember { mutableStateOf(false) }
    var pxPerMm by remember { mutableFloatStateOf(RulerCalibration.get(context)) }
    // where the slidable line is, in millimetres from the zero edge, so it survives a recalibration
    var lineMm by rememberSaveable { mutableFloatStateOf(50f) }
    var heightPx by remember { mutableFloatStateOf(0f) }

    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primaryContainer

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(pxPerMm) { detectTapGestures { lineMm = it.y / pxPerMm } }
                .pointerInput(pxPerMm) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        lineMm = ((lineMm * pxPerMm + dy) / pxPerMm).coerceIn(0f, size.height / pxPerMm)
                    }
                },
        ) {
            heightPx = size.height
            drawRect(bg)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink.toArgb()
                textSize = 15.dp.toPx()
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            val stepMm = if (metric) 1f else 25.4f / 16f
            val perUnit = if (metric) 10 else 16
            val total = (size.height / pxPerMm / stepMm).toInt()
            for (i in 0..total) {
                val y = i * stepMm * pxPerMm
                val len = if (metric) {
                    when {
                        i % 10 == 0 -> 52.dp.toPx()
                        i % 5 == 0 -> 36.dp.toPx()
                        else -> 22.dp.toPx()
                    }
                } else {
                    when {
                        i % 16 == 0 -> 52.dp.toPx()
                        i % 8 == 0 -> 40.dp.toPx()
                        i % 4 == 0 -> 30.dp.toPx()
                        i % 2 == 0 -> 22.dp.toPx()
                        else -> 13.dp.toPx()
                    }
                }
                drawLine(ink, Offset(0f, y), Offset(len, y), strokeWidth = 1.5.dp.toPx())
                if (i % perUnit == 0) {
                    drawIntoCanvas { it.nativeCanvas.drawText((i / perUnit).toString(), len + 8.dp.toPx(), y + paint.textSize * 0.9f, paint) }
                }
            }
            // the slidable line
            val ly = lineMm * pxPerMm
            drawLine(accent, Offset(0f, ly), Offset(size.width, ly), strokeWidth = 3.dp.toPx())
            drawCircle(accent, radius = 9.dp.toPx(), center = Offset(size.width - 28.dp.toPx(), ly))
        }

        // the distance of the line from zero
        val main = if (metric) "%.2f cm".format(lineMm / 10f) else "%.3f in".format(lineMm / 25.4f)
        val other = if (metric) "%.3f in".format(lineMm / 25.4f) else "%.2f cm".format(lineMm / 10f)
        // a plain box, not a Surface: a Surface swallows touches, and the label should be draggable like the line
        val pillShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 112.dp)
                .offset { IntOffset(0, (lineMm * pxPerMm - 34.dp.toPx()).roundToInt().coerceAtLeast(0)) }
                .shadow(4.dp, pillShape)
                .background(accent, pillShape),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(main, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(other, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // floating controls
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer, // same as the navigation bar
            shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(20.dp),
        ) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Choice(listOf("cm", "in"), if (metric) 0 else 1, Modifier.width(128.dp)) { metric = it == 0 }
                FilledTonalButton(onClick = { calibrating = true }) {
                    Icon(painterResource(R.drawable.tool_card), contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Calibrate")
                }
            }
        }
    }

    if (calibrating) {
        CalibrateDialog(
            current = pxPerMm,
            default = RulerCalibration.default(context),
            onSave = { RulerCalibration.save(context, it); pxPerMm = it; calibrating = false },
            onReset = { RulerCalibration.reset(context); pxPerMm = RulerCalibration.default(context); calibrating = false },
            onDismiss = { calibrating = false },
        )
    }
}

/** Shows a credit-card-sized outline; the user changes its size until a real card matches it. */
@Composable
private fun CalibrateDialog(current: Float, default: Float, onSave: (Float) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var ppm by remember { mutableFloatStateOf(current) }
    val outline = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = RulerCalibration.CARD_WIDTH_MM * ppm
                    val h = RulerCalibration.CARD_HEIGHT_MM * ppm
                    val topLeft = Offset((size.width - w) / 2f, size.height * 0.2f)
                    val r = CornerRadius(3.18f * ppm)
                    drawRoundRect(fill, topLeft, Size(w, h), r)
                    drawRoundRect(outline, topLeft, Size(w, h), r, style = Stroke(2.dp.toPx()))
                }
                Column(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Calibrate the ruler", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Hold a bank card upright against the screen. Drag the slider until the outline is exactly as wide as the card.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { ppm = (ppm * 0.998f) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Slider(
                            value = ppm / default, onValueChange = { ppm = it * default }, valueRange = 0.6f..1.5f,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { ppm = (ppm * 1.002f) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = { onSave(ppm) }, modifier = Modifier.weight(1f)) { Text("Save") }
                    }
                }
            }
        }
    }
}
