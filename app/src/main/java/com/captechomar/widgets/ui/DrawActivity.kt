package com.captechomar.widgets.ui

import android.appwidget.AppWidgetManager
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.R
import com.captechomar.widgets.data.DrawingStore
import com.captechomar.widgets.data.Stroke
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.DrawingKind
import com.captechomar.widgets.widgets.DrawingRenderer
import com.captechomar.widgets.widgets.Palettes
import com.captechomar.widgets.widgets.WidgetUpdater
import kotlinx.coroutines.delay

/** What is on the canvas, what can be redone, and the stroke being drawn right now. */
private class DrawState(initial: List<Stroke>) {
    val strokes = mutableStateListOf<Stroke>().also { it.addAll(initial) }
    val redo = mutableStateListOf<Stroke>()
    var erasing by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    private var live: ArrayList<Float>? = null

    fun begin(x: Float, y: Float) {
        live = arrayListOf(x, y)
        tick++
    }

    fun add(x: Float, y: Float) {
        val l = live ?: return
        val n = l.size
        if (n >= 2) {
            val dx = x - l[n - 2]
            val dy = y - l[n - 1]
            if (dx * dx + dy * dy < 1e-6f) return
        }
        l.add(x)
        l.add(y)
        tick++
    }

    fun end() {
        val l = live ?: return
        strokes.add(Stroke(if (erasing) Stroke.ERASE else Stroke.INK, l.toFloatArray()))
        redo.clear()
        live = null
        tick++
    }

    fun liveStroke(): Stroke? = live?.let { Stroke(if (erasing) Stroke.ERASE else Stroke.INK, it.toFloatArray()) }
    val drawing: Boolean get() = live != null

    fun undo() {
        if (strokes.isNotEmpty()) redo.add(strokes.removeAt(strokes.lastIndex))
        tick++
    }

    fun redoStroke() {
        if (redo.isNotEmpty()) strokes.add(redo.removeAt(redo.lastIndex))
        tick++
    }

    fun clear() {
        if (strokes.none { it.type != Stroke.CLEAR }) return
        strokes.add(Stroke(Stroke.CLEAR, FloatArray(0)))
        redo.clear()
        tick++
    }
}

/**
 * Opened by tapping a drawing widget: a card with a canvas the shape of the widget, the pen, an eraser,
 * undo and redo. Whatever is drawn is saved and the widget is redrawn when the screen closes.
 */
class DrawActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var aspect = 0.5f
    private var state: DrawState? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val cfg = WidgetPrefs.load(this, widgetId, DrawingKind.defaultConfig)
        aspect = WidgetUpdater.aspect(this, widgetId)
        val s = DrawState(DrawingStore.load(this, widgetId)?.strokes ?: emptyList())
        state = s
        val col = Palettes.resolve(this, cfg.palette)
        val corner = 0.14f * (0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)) * 1.4f

        setContent {
            AppTheme {
                var shown by remember { mutableStateOf(false) }
                var closing by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                LaunchedEffect(closing) {
                    if (closing) {
                        shown = false
                        delay(230)
                        finish()
                        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                    }
                }
                val scrim by animateFloatAsState(if (shown) 0.55f else 0f, tween(260), label = "scrim")
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrim))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { closing = true }
                        .systemBarsPadding()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // the canvas has the widget's proportions, as large as fits above the buttons
                    val cardPad = 16.dp
                    val canvasW = minOf(maxWidth - cardPad * 2, (maxHeight - cardPad * 2 - 150.dp) / aspect).coerceAtLeast(120.dp)
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f),
                        exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.9f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                        ) {
                            Column(
                                Modifier.padding(cardPad),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                DrawingCanvas(
                                    s, Color(col.container), Color(col.content), corner,
                                    Modifier.width(canvasW).height(canvasW * aspect),
                                )
                                Toolbar(s)
                                Button(onClick = { closing = true }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun persist() {
        val s = state ?: return
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        DrawingStore.save(this, widgetId, aspect, s.strokes.toList())
        WidgetUpdater.update(this, DrawingKind, widgetId)
    }

    override fun onPause() {
        persist()
        super.onPause()
    }
}

@Composable
private fun DrawingCanvas(state: DrawState, bg: Color, ink: Color, corner: Float, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val radius = maxHeight * corner
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radius)).background(bg)) {
            state.tick
            val empty = state.strokes.none { it.type != Stroke.CLEAR } && !state.drawing
            if (empty) {
                Text(
                    "Draw here",
                    style = MaterialTheme.typography.titleLarge,
                    color = ink,
                    modifier = Modifier.align(Alignment.Center).alpha(0.5f),
                )
            }
            androidx.compose.foundation.Canvas(
                Modifier.fillMaxSize().pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        down.consume()
                        state.begin(down.position.x / w, down.position.y / w)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            for (h in change.historical) state.add(h.position.x / w, h.position.y / w)
                            state.add(change.position.x / w, change.position.y / w)
                            change.consume()
                        }
                        state.end()
                    }
                },
            ) {
                state.tick // redraw while a stroke is in progress
                val list = state.strokes.toList() + listOfNotNull(state.liveStroke())
                drawIntoCanvas {
                    DrawingRenderer.drawStrokes(
                        it.nativeCanvas, list, 0f, 0f, size.width, ink.toArgb(),
                        RectF(0f, 0f, size.width, size.height),
                    )
                }
            }
        }
    }
}

@Composable
private fun Toolbar(state: DrawState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        FilledIconToggleButton(checked = state.erasing, onCheckedChange = { state.erasing = it }) {
            Icon(painterResource(R.drawable.dr_eraser), contentDescription = "Eraser")
        }
        IconButton(onClick = { state.undo() }, enabled = state.strokes.isNotEmpty()) {
            Icon(painterResource(R.drawable.dr_undo), contentDescription = "Undo")
        }
        IconButton(onClick = { state.redoStroke() }, enabled = state.redo.isNotEmpty()) {
            Icon(painterResource(R.drawable.dr_redo), contentDescription = "Redo")
        }
        IconButton(onClick = { state.clear() }, enabled = state.strokes.any { it.type != Stroke.CLEAR }) {
            Icon(painterResource(R.drawable.dr_clear), contentDescription = "Clear")
        }
    }
}
