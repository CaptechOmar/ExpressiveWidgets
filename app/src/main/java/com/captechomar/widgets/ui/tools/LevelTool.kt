package com.captechomar.widgets.ui.tools

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.captechomar.widgets.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Geometry is in fractions of the card width so the same numbers drive the drawing and the haptics.
private const val OUTLINE = 0.43f // width of the outline shape
private const val FILL = 0.82f // the floating shape, relative to the outline, when level
private const val DRIFT = 0.0048f // how far the shape drifts per degree of tilt
private const val LEVEL_IN = 1.0f // degrees at which it counts as level
private const val LEVEL_OUT = 1.6f // degrees at which it stops counting as level

private enum class Zone { NONE, SLOW, FAST, LEVEL }

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Suppress("DEPRECATION")
private fun levelVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

private fun lightTick(v: Vibrator?) {
    v ?: return
    try {
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(8, 90))
    } catch (_: Throwable) {
    }
}

private fun strongTick(v: Vibrator?) {
    v ?: return
    try {
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        else v.vibrate(VibrationEffect.createOneShot(40, 255))
    } catch (_: Throwable) {
    }
}

private fun degrees(v: Float) = Math.toDegrees(asin(v.coerceIn(-1f, 1f)).toDouble()).toFloat()

private fun signed(v: Float): String {
    val r = (v * 10).roundToInt() / 10f
    return "%.1f°".format(if (r == 0f) 0f else r)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun LevelTool() {
    val context = LocalContext.current
    val view = LocalView.current
    val (available, accel) = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    if (!available) return Missing("motion")
    val scheme = MaterialTheme.colorScheme
    val prefs = remember { context.applicationContext.getSharedPreferences("tools", Context.MODE_PRIVATE) }
    val vibrator = remember { levelVibrator(context) }

    var haptic by remember { mutableStateOf(prefs.getBoolean("level_haptic", true)) }
    var awake by remember { mutableStateOf(prefs.getBoolean("level_awake", true)) }
    var locked by remember { mutableStateOf(false) }
    var calRoll by remember { mutableFloatStateOf(prefs.getFloat("level_cal_roll", 0f)) }
    var calPitch by remember { mutableFloatStateOf(prefs.getFloat("level_cal_pitch", 0f)) }
    var calibrated by remember { mutableStateOf(prefs.getBoolean("level_calibrated", false)) }
    var sheet by remember { mutableIntStateOf(-1) }

    // the phone's reading, smoothed
    val g = SensorManager.GRAVITY_EARTH
    var smooth by remember { mutableStateOf(floatArrayOf(0f, 0f, g)) }
    val raw = accel.value
    LaunchedEffect(raw) { if (raw != null) smooth = FloatArray(3) { smooth[it] + (raw[it] - smooth[it]) * 0.25f } }

    // roll is the sideways tilt and pitch the forward one; both are measured from the calibrated surface
    val rawRoll = degrees(-smooth[0] / g)
    val rawPitch = degrees(-smooth[1] / g)
    val roll = rawRoll - calRoll
    val pitch = rawPitch - calPitch
    val incline = hypot(roll, pitch)

    val levelState = remember { mutableStateOf(false) }
    val level = if (levelState.value) incline <= LEVEL_OUT else incline <= LEVEL_IN
    SideEffect { levelState.value = level }

    // where the floating shape sits, how stretched it is, and how far into a different shape it has melted
    var dx = -roll * DRIFT
    var dy = pitch * DRIFT
    val mag = hypot(dx, dy)
    if (mag > 0.25f) {
        dx *= 0.25f / mag
        dy *= 0.25f / mag
    }
    val stretch = (incline / 25f).coerceIn(0f, 1f)
    val outlineR = OUTLINE / 2f
    val blobR = outlineR * FILL * (1f + 0.9f * stretch)
    val drift = min(mag, 0.25f)
    val zone = when {
        level -> Zone.LEVEL
        drift + blobR <= outlineR -> Zone.FAST // fully inside the outline, not level yet
        incline <= 15f -> Zone.SLOW // touching the outline
        else -> Zone.NONE
    }

    val spring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
    val animDx by animateFloatAsState(dx, spring, label = "dx")
    val animDy by animateFloatAsState(dy, spring, label = "dy")
    val animStretch by animateFloatAsState(stretch, spring, label = "stretch")
    val animMelt by animateFloatAsState((incline / 22f).coerceIn(0f, 1f), spring, label = "melt")
    val levelF by animateFloatAsState(if (level) 1f else 0f, tween(500), label = "levelF")

    // haptics: slow ticks on the outline, fast ticks inside it, one firm tick on arriving at level
    val lastZone = remember { arrayOf(Zone.NONE) }
    LaunchedEffect(zone, haptic) {
        val before = lastZone[0]
        lastZone[0] = zone
        if (!haptic) return@LaunchedEffect
        when (zone) {
            Zone.SLOW -> while (true) {
                lightTick(vibrator)
                delay(450)
            }
            Zone.FAST -> while (true) {
                lightTick(vibrator)
                delay(110)
            }
            Zone.LEVEL -> if (before == Zone.FAST || before == Zone.SLOW) strongTick(vibrator)
            Zone.NONE -> Unit
        }
    }

    DisposableEffect(awake) {
        val old = view.keepScreenOn
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = old }
    }
    DisposableEffect(locked) {
        val activity = context.findActivity()
        if (locked) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { if (locked) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    fun calibrate() {
        calRoll = rawRoll
        calPitch = rawPitch
        calibrated = true
        prefs.edit().putFloat("level_cal_roll", rawRoll).putFloat("level_cal_pitch", rawPitch).putBoolean("level_calibrated", true).apply()
        if (haptic) strongTick(vibrator)
    }

    fun resetCalibration() {
        calRoll = 0f
        calPitch = 0f
        calibrated = false
        prefs.edit().putFloat("level_cal_roll", 0f).putFloat("level_cal_pitch", 0f).putBoolean("level_calibrated", false).apply()
    }

    // the colours: mint when moving, blue when level
    val centre = lerp(scheme.primaryContainer, scheme.tertiary, levelF)
    val fillColor = lerp(centre, Color.White, 0.65f)
    val ringColor = lerp(lerp(centre, Color.Black, 0.72f), lerp(centre, Color.White, 0.45f), levelF)
    val inkColor = lerp(centre, Color.Black, 0.65f)
    val textColor = lerp(centre, Color.White, 0.88f)
    val barBg = lerp(scheme.secondaryContainer, scheme.primaryContainer, levelF)
    val barFg = lerp(scheme.onSecondaryContainer, scheme.onPrimaryContainer, levelF)
    val flipBg = lerp(scheme.primary, lerp(scheme.primary, Color.White, 0.6f), levelF)

    val outlineShape = MaterialShapes.Cookie6Sided.toShape()
    val morphA = remember { Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Puffy) }
    val morphB = remember { Morph(MaterialShapes.Puffy, MaterialShapes.Oval) }

    // water: a slow loop for the waves, a quicker one for the wobble of the shape
    val loop = rememberInfiniteTransition(label = "loop")
    val wave by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "wave")
    val wobble by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wobble")
    val wob = sin(wobble * 2f * PI.toFloat())
    val wobCos = cos(wobble * 2f * PI.toFloat())
    val melt = (animMelt + 0.07f * wob * min(1f, incline / 3f)).coerceIn(0f, 1f)
    val blobShape = if (melt < 0.5f) morphA.toShape(melt * 2f) else morphB.toShape((melt - 0.5f) * 2f)

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                Text("Level", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = scheme.tertiary)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(36.dp))) {
                val w = maxWidth
                val h = maxHeight
                val cx = w / 2
                val cy = h * 0.43f
                val outlineSize = w * OUTLINE
                val fillSize = outlineSize * FILL

                // the background: a drop of colour with ripples spreading from it
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2, size.height * 0.43f)
                    val pulse = 0.58f + 0.03f * sin(wave * 2f * PI.toFloat())
                    drawRect(
                        Brush.radialGradient(
                            0f to centre, 0.42f to centre.copy(alpha = 0.95f), 1f to lerp(centre, Color.Black, 0.82f),
                            center = c, radius = size.height * pulse,
                        ),
                    )
                    for (i in 0 until 3) {
                        val ph = (wave + i / 3f) % 1f
                        val col = if (i % 2 == 0) scheme.tertiary else scheme.primaryContainer
                        drawCircle(
                            col.copy(alpha = (1f - ph) * 0.28f), radius = ph * size.width * 0.95f, center = c,
                            style = Stroke(width = size.width * (0.03f + 0.05f * ph)),
                        )
                    }
                }

                // the outline the shape has to settle into
                Box(
                    Modifier
                        .size(outlineSize)
                        .offset(cx - outlineSize / 2, cy - outlineSize / 2)
                        .border((3f + 2f * levelF).dp, ringColor, outlineShape),
                )

                // the shape itself, melting towards the direction it is moving in
                val angle = Math.toDegrees(atan2(animDy.toDouble(), animDx.toDouble())).toFloat()
                Box(
                    Modifier
                        .size(fillSize)
                        .offset(cx + w * animDx - fillSize / 2, cy + w * animDy - fillSize / 2)
                        .graphicsLayer {
                            rotationZ = angle
                            scaleX = 1f + 0.9f * animStretch + 0.025f * wob * animStretch
                            scaleY = 1f - 0.12f * animStretch + 0.02f * wobCos * animStretch
                        }
                        .shadow(10.dp, blobShape)
                        .clip(blobShape)
                        .background(fillColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (calibrated) R.drawable.lv_calibrate else R.drawable.lv_check),
                        contentDescription = if (level) "Level" else null,
                        tint = inkColor.copy(alpha = levelF),
                        modifier = Modifier.size(fillSize * 0.42f).graphicsLayer { rotationZ = -angle },
                    )
                }

                // readouts: roll above, pitch and incline curving round the lower sides
                val big = (w.value * 0.07f).sp
                val small = (w.value * 0.036f).sp
                Readout(Modifier.offset(cx - 75.dp, cy - w * 0.36f - 45.dp), signed(roll), "Roll", big, small, textColor, labelFirst = true)
                for ((phi, value, label) in listOf(Triple(133.0, signed(pitch), "Pitch"), Triple(47.0, signed(incline), "Incline"))) {
                    val r = Math.toRadians(phi)
                    Readout(
                        Modifier
                            .offset(cx + w * (0.34f * cos(r)).toFloat() - 75.dp, cy + w * (0.34f * sin(r)).toFloat() - 45.dp)
                            .rotate((phi - 90.0).toFloat()),
                        value, label, big, small, textColor, labelFirst = false,
                    )
                }

                // the option bar and the flip-to-align button
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(barBg).padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BarButton(if (locked) R.drawable.lv_lock else R.drawable.lv_unlock, "Lock orientation", locked, barFg, { locked = !locked }, { sheet = 0 })
                        BarButton(R.drawable.lv_vibrate, "Haptic feedback", haptic, barFg, {
                            haptic = !haptic
                            prefs.edit().putBoolean("level_haptic", haptic).apply()
                        }, { sheet = 1 })
                        BarButton(R.drawable.lv_bulb, "Keep screen awake", awake, barFg, {
                            awake = !awake
                            prefs.edit().putBoolean("level_awake", awake).apply()
                        }, { sheet = 2 })
                        BarButton(
                            if (calibrated) R.drawable.lv_reset else R.drawable.lv_calibrate,
                            if (calibrated) "Reset calibration" else "Surface calibration", calibrated, barFg,
                            { if (calibrated) resetCalibration() else calibrate() }, { sheet = 3 },
                        )
                    }
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(flipBg)
                            .combinedClickable(onClick = { }, onLongClick = { sheet = 4 }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.lv_flip), contentDescription = "Flip to align", tint = scheme.onPrimary, modifier = Modifier.size(28.dp)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    when (sheet) {
        0 -> InfoSheet(
            if (locked) R.drawable.lv_lock else R.drawable.lv_unlock, "Lock Orientation",
            "Stops the screen from rotating while you measure, so turning the phone doesn't flip the layout.",
            "Got it", { sheet = -1 }, { sheet = -1 },
        )
        1 -> InfoSheet(
            R.drawable.lv_vibrate, "Haptic Feedback",
            "Feel it without looking. Slow ticks when the shape touches the outline, fast ticks once it is inside, and one firm tick when you reach level.",
            "Got it", { sheet = -1 }, { sheet = -1 },
        )
        2 -> InfoSheet(
            R.drawable.lv_bulb, "Keep Screen Awake",
            "Prevents the screen from turning off automatically while you are working.",
            "Got it", { sheet = -1 }, { sheet = -1 },
        )
        3 -> InfoSheet(
            R.drawable.lv_calibrate, "Surface Calibration",
            "Place your device on a flat surface to set the 0° reference. Any tilt from a camera bump or a case is cancelled out. Tap the button again to go back to true level.",
            "Calibrate Now", { sheet = -1 }, { calibrate(); sheet = -1 },
            extra = { CalibrationAnimation() },
        )
        4 -> InfoSheet(
            R.drawable.lv_flip, "Flip to Align",
            "Coming soon.",
            "Got it", { sheet = -1 }, { sheet = -1 },
        )
    }
}

@Composable
private fun Readout(modifier: Modifier, value: String, label: String, big: androidx.compose.ui.unit.TextUnit, small: androidx.compose.ui.unit.TextUnit, color: Color, labelFirst: Boolean) {
    Box(modifier.size(150.dp, 90.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val l = @Composable { Text(label, fontSize = small, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.5f), textAlign = TextAlign.Center) }
            val v = @Composable { Text(value, fontSize = big, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center, maxLines = 1) }
            if (labelFirst) {
                l()
                v()
            } else {
                v()
                l()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarButton(icon: Int, description: String, active: Boolean, tint: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = "What $description does",
            ),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(26.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(
    icon: Int,
    title: String,
    text: String,
    buttonText: String,
    onDismiss: () -> Unit,
    onButton: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            extra()
            Button(onClick = onButton, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(buttonText, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** A phone resting on a camera bump settles flat, then the calibration target pops up. Loops. */
@Composable
private fun CalibrationAnimation() {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberInfiniteTransition(label = "calibration")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "t")
    fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
    val settle = smooth((t - 0.30f) / 0.25f)
    val pop = smooth((t - 0.66f) / 0.14f) * (1f - smooth((t - 0.94f) / 0.06f))
    val bump = 16.dp
    Box(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val floor = size.height * 0.80f
            val bodyW = size.width * 0.74f
            val bodyH = 16.dp.toPx()
            val left = (size.width - bodyW) / 2f
            val tilt = Math.toDegrees(asin((bump.toPx() / bodyW).toDouble())).toFloat() * (1f - settle)
            drawRoundRect(scheme.outlineVariant, Offset(0f, floor + 2.dp.toPx()), Size(size.width, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            rotate(-tilt, Offset(left + bodyW, floor)) {
                // the bump under the left end, then the body and its screen
                drawRoundRect(Color(0xFF8E94A8), Offset(left + 14.dp.toPx(), floor - 2.dp.toPx()), Size(44.dp.toPx(), bump.toPx() + 2.dp.toPx()), CornerRadius(6.dp.toPx()))
                drawRoundRect(Color(0xFFC3C8DA), Offset(left, floor - bodyH), Size(bodyW, bodyH), CornerRadius(7.dp.toPx()))
                drawRoundRect(Color(0xFF06201C), Offset(left + 5.dp.toPx(), floor - bodyH + 2.dp.toPx()), Size(bodyW - 10.dp.toPx(), 7.dp.toPx()), CornerRadius(3.dp.toPx()))
                drawOval(scheme.primary.copy(alpha = 0.9f * settle), Offset(size.width * 0.38f, floor - bodyH + 1.dp.toPx()), Size(size.width * 0.24f, 9.dp.toPx()))
            }
        }
        Text(
            signed(-5f * 0.22f * (1f - settle)),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = (-6).dp)
                .size(72.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                    alpha = pop
                }
                .clip(CircleShape)
                .background(scheme.tertiary),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.lv_calibrate), contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(38.dp)) }
    }
}
