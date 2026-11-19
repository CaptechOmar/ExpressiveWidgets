package com.captechomar.widgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
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
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.Calc
import com.captechomar.widgets.data.SavingsStore
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.SavingsKind
import com.captechomar.widgets.widgets.SavingsRenderer

/** Fades and lifts a piece of content in after [delayMs], so the pieces of the calculator arrive one by one. */
@Composable
private fun Modifier.reveal(show: Boolean, delayMs: Int): Modifier {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) { delay(delayMs.toLong()); on = true } else on = false
    }
    val t by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "reveal")
    return this.graphicsLayer { alpha = t; translationY = (1f - t) * 24.dp.toPx() }
}

/** The calculator itself. [animate] staggers its parts in and puts the cursor in the amount box straight away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdjustContent(
    currency: String, saved: Double, onApply: (newSaved: Double) -> Unit, onDismiss: () -> Unit,
    animate: Boolean = false, visible: Boolean = true,
) {
    var input by remember { mutableStateOf("") }
    val value = Calc.eval(input)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (animate) {
        LaunchedEffect(Unit) {
            delay(180)
            focus.requestFocus()
            keyboard?.show()
        }
    }
    @Composable fun m(delayMs: Int): Modifier = if (animate) Modifier.reveal(visible, delayMs) else Modifier
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Adjust savings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.then(m(40)))
        Text(
            "Saved now: ${SavingsRenderer.format(currency, saved)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.then(m(90)),
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Amount, e.g. 120+35") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).then(m(140)),
            supportingText = { if (value != null && input.any { !it.isDigit() && it != '.' }) Text("= ${SavingsRenderer.format("", value)}") },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.then(m(200))) {
            Button(enabled = value != null, onClick = { onApply(saved + value!!) }) { Text("Add") }
            FilledTonalButton(enabled = value != null, onClick = { onApply(saved - value!!) }) { Text("Subtract") }
            OutlinedButton(enabled = value != null, onClick = { onApply(value!!) }) { Text("Set to") }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).then(m(250))) { Text("Cancel") }
    }
}

/** Quick calculator as a plain dialog, used from the settings screen. */
@Composable
fun AdjustDialog(currency: String, saved: Double, onApply: (newSaved: Double) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { AdjustContent(currency, saved, onApply, onDismiss) },
        confirmButton = {},
    )
}

/** Opened from the emoji tile of a savings widget: a floating calculator that animates in over the home screen. */
class SavingsAdjustActivity : ComponentActivity() {
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
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val cfg = WidgetPrefs.load(this, id, SavingsKind.defaultConfig)
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
                        overridePendingTransition(0, 0)
                    }
                }
                val scrim by animateFloatAsState(if (shown) 0.55f else 0f, tween(260), label = "scrim")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrim))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { closing = true }
                        .systemBarsPadding()
                        .imePadding()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(180)) +
                            scaleIn(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f) +
                            slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 5 },
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
                            Column(Modifier.padding(24.dp)) {
                                AdjustContent(
                                    currency = cfg.savings.currency,
                                    saved = cfg.savings.saved,
                                    onApply = { v ->
                                        SavingsStore.mutate(this@SavingsAdjustActivity, id) { it.withSaved(v) }
                                        closing = true
                                    },
                                    onDismiss = { closing = true },
                                    animate = true,
                                    visible = shown,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
