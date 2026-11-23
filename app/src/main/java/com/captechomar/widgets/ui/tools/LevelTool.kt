package com.captechomar.widgets.ui.tools

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Geometry is in fractions of the card width so the same numbers drive the drawing and the haptics.
private const val OUTLINE = 0.43f // width of the outline shape
private const val FILL = 0.82f // the floating shape, relative to the outline, when level
private const val DRIFT = 0.0048f // how far the shape drifts per degree of tilt
private const val LEVEL_IN = 1.0f // degrees at which it counts as level
private const val LEVEL_OUT = 1.6f // degrees at which it stops counting as level
private const val BOX = 2.2f // the floating shape lives in a box this many times its own size, so it can stretch

private enum class Zone { NONE, SLOW, FAST, LEVEL }

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

private fun clickTick(v: Vibrator?) {
    v ?: return
    try {
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        else v.vibrate(VibrationEffect.createOneShot(18, 200))
    } catch (_: Throwable) {
    }
}

private fun degrees(v: Float) = Math.toDegrees(asin(v.coerceIn(-1f, 1f)).toDouble()).toFloat()

private fun signed(v: Float): String {
    val r = (v * 10).roundToInt() / 10f
    return "%.1f°".format(if (r == 0f) 0f else r)
}

private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
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
    var frozen by remember { mutableStateOf<FloatArray?>(null) } // the readings held by the lock
    var calRoll by remember { mutableFloatStateOf(prefs.getFloat("level_cal_roll", 0f)) }
    var calPitch by remember { mutableFloatStateOf(prefs.getFloat("level_cal_pitch", 0f)) }
    var calibrated by remember { mutableStateOf(prefs.getBoolean("level_calibrated", false)) }
    var sheet by remember { mutableIntStateOf(-1) }
    val locked = frozen != null

    // the phone's reading, smoothed
    val g = SensorManager.GRAVITY_EARTH
    var smoothed by remember { mutableStateOf(floatArrayOf(0f, 0f, g)) }
    val raw = accel.value
    LaunchedEffect(raw) { if (raw != null) smoothed = FloatArray(3) { smoothed[it] + (raw[it] - smoothed[it]) * 0.25f } }

    // roll is the sideways tilt and pitch the forward one; both are measured from the calibrated surface
    val liveRoll = degrees(-smoothed[0] / g)
    val livePitch = degrees(-smoothed[1] / g)
    val rawRoll = frozen?.get(0) ?: liveRoll
    val rawPitch = frozen?.get(1) ?: livePitch
    val roll = rawRoll - calRoll
    val pitch = rawPitch - calPitch
    val incline = hypot(roll, pitch)

    val levelState = remember { mutableStateOf(false) }
    val level = if (levelState.value) incline <= LEVEL_OUT else incline <= LEVEL_IN
    SideEffect { levelState.value = level }

    // where the floating shape wants to be, and how it wants to be stretched
    var dx = -roll * DRIFT
    var dy = pitch * DRIFT
    val mag = hypot(dx, dy)
    if (mag > 0.25f) {
        dx *= 0.25f / mag
        dy *= 0.25f / mag
    }
    if (level) {
        dx = 0f
        dy = 0f
    }
    val inflate = (incline / 10f).coerceIn(0f, 1f)
    val thin = ((incline - 9f) / 21f).coerceIn(0f, 1f)
    val along = 1f + 0.35f * inflate + 0.25f * thin
    val across = 1f + 0.12f * inflate - 0.32f * thin
    val melt = (incline / 26f).coerceIn(0f, 1f)

    val outlineR = OUTLINE / 2f
    val blobR = outlineR * FILL * max(along, across)
    val drift = min(mag, 0.25f)
    val zone = when {
        locked -> Zone.NONE
        level -> Zone.LEVEL
        drift + blobR <= outlineR -> Zone.FAST // fully inside the outline, not level yet
        incline <= 15f -> Zone.SLOW // touching the outline
        else -> Zone.NONE
    }

    // springs with a bounce, so the shape floats and settles instead of snapping
    val bouncy = spring<Float>(dampingRatio = 0.42f, stiffness = 90f)
    val bouncyShape = spring<Float>(dampingRatio = 0.5f, stiffness = 120f)
    val animDx by animateFloatAsState(dx, bouncy, label = "dx")
    val animDy by animateFloatAsState(dy, bouncy, label = "dy")
    val animAlong by animateFloatAsState(along, bouncyShape, label = "along")
    val animAcross by animateFloatAsState(across, bouncyShape, label = "across")
    val animMelt by animateFloatAsState(melt, bouncyShape, label = "melt")
    val levelF by animateFloatAsState(if (level) 1f else 0f, tween(500), label = "levelF")
    val lockF by animateFloatAsState(if (locked) 1f else 0f, tween(400), label = "lockF")

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

    fun calibrate() {
        calRoll = liveRoll
        calPitch = livePitch
        calibrated = true
        prefs.edit().putFloat("level_cal_roll", liveRoll).putFloat("level_cal_pitch", livePitch).putBoolean("level_calibrated", true).apply()
        if (haptic) strongTick(vibrator)
    }

    fun resetCalibration() {
        calRoll = 0f
        calPitch = 0f
        calibrated = false
        prefs.edit().putFloat("level_cal_roll", 0f).putFloat("level_cal_pitch", 0f).putBoolean("level_calibrated", false).apply()
    }

    // the colours: mint when moving, blue when level, dimmed while the readings are locked
    val centre = lerp(lerp(scheme.primaryContainer, scheme.tertiary, levelF), Color.Black, 0.42f * lockF)
    val fillColor = lerp(lerp(centre, Color.White, 0.65f), scheme.primary, lockF)
    val ringColor = lerp(lerp(centre, Color.Black, 0.72f), lerp(centre, Color.White, 0.45f), levelF)
    val inkColor = lerp(centre, Color.Black, 0.65f)
    val textColor = lerp(centre, Color.White, 0.88f)
    val barBg = lerp(scheme.secondaryContainer, scheme.primaryContainer, levelF)
    val barFg = lerp(scheme.onSecondaryContainer, scheme.onPrimaryContainer, levelF)
    val flipBg = lerp(scheme.primary, lerp(scheme.primary, Color.White, 0.6f), levelF)

    val outlineShape = MaterialShapes.Cookie6Sided.toShape()
    val chain = remember {
        listOf(
            Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Circle),
            Morph(MaterialShapes.Circle, MaterialShapes.Square),
            Morph(MaterialShapes.Square, MaterialShapes.Oval),
        )
    }

    // water and float: a slow loop for the waves, two slightly different ones for the shape drifting on the spot
    val loop = rememberInfiniteTransition(label = "loop")
    val wave by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "wave")
    val floatX by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(4300, easing = LinearEasing)), label = "floatX")
    val floatY by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(5300, easing = LinearEasing)), label = "floatY")
    val breathe by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3100, easing = LinearEasing)), label = "breathe")
    val floatAmp = (0.012f * (0.5f + 0.5f * levelF)) * (1f - 0.8f * lockF)
    val floatDx = floatAmp * sin(floatX * 2f * PI.toFloat())
    val floatDy = floatAmp * sin(floatY * 2f * PI.toFloat() + 1f)
    val breath = 1f + 0.02f * sin(breathe * 2f * PI.toFloat()) * (1f - lockF)

    // the stretch follows the direction of travel; it fades out when the shape is on the spot so nothing twitches
    val dirConf = (hypot(animDx, animDy) / 0.012f).coerceIn(0f, 1f)
    val shownAlong = 1f + (animAlong - 1f) * dirConf
    val shownAcross = 1f + (animAcross - 1f) * dirConf
    val angle = Math.toDegrees(atan2(animDy.toDouble(), animDx.toDouble())).toFloat()
    val blobShape: Shape = MorphShape(chain, animMelt.coerceIn(0f, 1f), shownAlong, shownAcross, angle)

    val press = remember { { clickTick(vibrator) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val outerH = maxHeight
        // the card is a little shorter than the page: 5% off the top, 10% off the bottom
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(top = outerH * 0.05f, bottom = outerH * 0.10f).clip(RoundedCornerShape(36.dp)),
        ) {
            val w = maxWidth
            val h = maxHeight
            val cx = w / 2
            val cy = (h - 96.dp) / 2 + 6.dp
            val outlineSize = w * OUTLINE
            val fillSize = outlineSize * FILL
            val boxSize = fillSize * BOX

            // the background: a drop of colour with ripples spreading from it
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, cy.toPx())
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
                        col.copy(alpha = (1f - ph) * 0.28f * (1f - lockF)), radius = ph * size.width * 0.95f, center = c,
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

            // the floating shape: it melts through a circle and a rounded square into a thin oval as the tilt grows
            Box(
                Modifier
                    .size(boxSize)
                    .offset(cx + w * (animDx + floatDx) - boxSize / 2, cy + w * (animDy + floatDy) - boxSize / 2)
                    .graphicsLayer {
                        scaleX = breath
                        scaleY = breath
                    }
                    .shadow(10.dp, blobShape)
                    .clip(blobShape)
                    .background(fillColor),
                contentAlignment = Alignment.Center,
            ) {
                val showLock = lockF
                val showCheck = levelF * (1f - lockF)
                Icon(
                    painterResource(R.drawable.lv_lock),
                    contentDescription = if (locked) "Readings locked" else null,
                    tint = inkColor.copy(alpha = showLock),
                    modifier = Modifier.size(fillSize * 0.42f),
                )
                Icon(
                    painterResource(if (calibrated) R.drawable.lv_calibrate else R.drawable.lv_check),
                    contentDescription = if (level) "Level" else null,
                    tint = inkColor.copy(alpha = showCheck),
                    modifier = Modifier.size(fillSize * 0.42f),
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
                    BarButton(if (locked) R.drawable.lv_lock else R.drawable.lv_unlock, "Lock readings", locked, barFg, {
                        press()
                        frozen = if (locked) null else floatArrayOf(liveRoll, livePitch)
                    }, { sheet = 0 })
                    BarButton(R.drawable.lv_vibrate, "Haptic feedback", haptic, barFg, {
                        press()
                        haptic = !haptic
                        prefs.edit().putBoolean("level_haptic", haptic).apply()
                    }, { sheet = 1 })
                    BarButton(R.drawable.lv_bulb, "Keep screen awake", awake, barFg, {
                        press()
                        awake = !awake
                        prefs.edit().putBoolean("level_awake", awake).apply()
                    }, { sheet = 2 })
                    BarButton(
                        if (calibrated) R.drawable.lv_reset else R.drawable.lv_calibrate,
                        if (calibrated) "Reset calibration" else "Surface calibration", calibrated, barFg,
                        {
                            press()
                            if (calibrated) resetCalibration() else calibrate()
                        },
                        { sheet = 3 },
                    )
                }
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(flipBg)
                        .combinedClickable(onClick = { press() }, onLongClick = { sheet = 4 }),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.lv_flip), contentDescription = "Flip to align", tint = scheme.onPrimary, modifier = Modifier.size(28.dp)) }
            }
        }
    }

    when (sheet) {
        0 -> InfoSheet(
            if (locked) R.drawable.lv_lock else R.drawable.lv_unlock, "Lock Readings",
            "Freeze the current pitch and roll angles on the screen. Useful for reading measurements in awkward positions.",
            "Got it", { sheet = -1 }, { sheet = -1 },
        )
        1 -> InfoSheet(
            R.drawable.lv_vibrate, "Haptic Feedback",
            "Feel a slight vibration when the device approaches perfect level. Helps you align without looking at the screen.",
            "Got it", { sheet = -1 }, { sheet = -1 },
        )
        2 -> InfoSheet(
            R.drawable.lv_bulb, "Keep Screen Awake",
            "Prevent the screen from turning off automatically while you are working.",
            "Got it", { sheet = -1 }, { sheet = -1 },
        )
        3 -> InfoSheet(
            R.drawable.lv_calibrate, "Surface Calibration",
            "Place your device on a flat surface to set the 0° reference. The Camera Bar protrusion is compensated during this process for maximum accuracy.",
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

/** A shape part-way along a chain of morphs, stretched along [angle], and centred in a box [BOX] times its own size. */
private class MorphShape(
    private val chain: List<Morph>,
    private val progress: Float,
    private val along: Float,
    private val across: Float,
    private val angle: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val seg = progress.coerceIn(0f, 1f) * chain.size
        val i = min(seg.toInt(), chain.size - 1)
        val cubics = chain[i].asCubics(seg - i)
        if (cubics.isNotEmpty()) {
            path.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
            for (c in cubics) path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
            path.close()
            val b = path.getBounds()
            val base = size.width / BOX
            val k = min(base / b.width, base / b.height)
            val m = Matrix().apply {
                translate(size.width / 2f, size.height / 2f)
                rotateZ(angle)
                scale(along, across)
                rotateZ(-angle)
                scale(k, k)
                translate(-b.center.x, -b.center.y)
            }
            path.transform(m)
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun Readout(modifier: Modifier, value: String, label: String, big: TextUnit, small: TextUnit, color: Color, labelFirst: Boolean) {
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
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

// One loop is 5.6 s. Every moment below is timed in milliseconds of the reference recording, where the loop runs 1400 -> 6900.
private const val LOOP_MS = 5600f
private const val LEAD_MS = 650f
private const val LOOP_START = 1400f

private fun calLin(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun calSeg(t: Float, a: Float, b: Float) = smooth(calLin(t, a, b))

private fun backOut(x: Float, k: Float = 1.7f): Float {
    val c = x.coerceIn(0f, 1f) - 1f
    return 1f + c * c * ((k + 1f) * c + k)
}

private class FaceState(
    val label: String,
    val glow: Float,
    val dim: Float,
    val liquidA: Float,
    val wave: Float,
    val blue: Float,
    val tabsA: Float,
    val dotOff: Float,
    val ringOff: Float,
    val ghostA: Float,
    val settle: Float,
    val mint: Int,
    val bubble: Int,
    val blueC: Int,
)

/**
 * The calibration explainer, following the reference recording frame by frame: the phone lies on its camera puck and
 * the cookie slides off its outline, the phone tips flat and the screen melts into a glowing dot, a reset icon pops up,
 * is pressed and becomes the calibrate icon while the glow turns blue and the cookie glides back to the centre of its
 * ring, then the icon shrinks away and the phone is picked up again.
 */
@Composable
private fun CalibrationAnimation() {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberInfiniteTransition(label = "calibration")
    // the first pass waits a little longer before the phone starts to tip, like the reference
    val clock = remember { Animatable(-LEAD_MS) }
    LaunchedEffect(Unit) {
        clock.animateTo(LOOP_MS, tween((LOOP_MS + LEAD_MS).toInt(), easing = LinearEasing))
        while (true) {
            clock.snapTo(0f)
            clock.animateTo(LOOP_MS, tween(LOOP_MS.toInt(), easing = LinearEasing))
        }
    }
    val ms = clock.value
    val wave by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "wave")
    val t = ms + LOOP_START

    val lean = (calSeg(t, 2100f, 2550f) - calSeg(t, 6750f, 6900f)).coerceIn(0f, 1f)
    val tiltIn = calLin(t, 2200f, 2720f)
    val tiltOut = calSeg(t, 6600f, 6900f)
    val settle = calSeg(t, 2800f, 3600f)
    val thetaDeg = (76f * (1f - (1f - tiltIn).pow(1.7f)) + 6f * settle) * (1f - tiltOut)
    val glow = (calSeg(t, 2450f, 2720f) - calSeg(t, 6730f, 6900f)).coerceIn(0f, 1f)
    val dim = calSeg(t, 2450f, 2560f) * (1f - calSeg(t, 6850f, 6900f))
    val liquidA = (1f - calSeg(t, 2560f, 2740f) + calSeg(t, 6820f, 6880f)).coerceIn(0f, 1f)
    val blue = (calSeg(t, 3940f, 4040f) - calSeg(t, 6440f, 6500f)).coerceIn(0f, 1f)

    // the cookie slides off its outline while the phone leans, then glides back once the button is pressed
    val slosh = calSeg(t, 2200f, 2650f)
    val glide = 1f - backOut(calLin(t, 4050f, 4480f), 1.2f)
    val dotOff = slosh * glide
    val ringOff = slosh * (1f - calSeg(t, 3930f, 4050f))
    val ghostA = (slosh * (1f - glow) + glow) * (1f - calSeg(t, 6780f, 6900f))

    // the icon pops up, is pressed (dips) and changes from reset to calibrate, then shrinks away
    val pop = backOut(calLin(t, 2950f, 3250f), 1.3f)
    val pressM = if (t < 3930f) 1f - 0.21f * calSeg(t, 3730f, 3830f) else 0.79f + 0.21f * backOut(calLin(t, 3930f, 4070f), 1.8f)
    val vanish = 1f - calSeg(t, 6430f, 6620f)
    val iconScale = pop * pressM * vanish
    val calMix = (calSeg(t, 3930f, 4070f) - calSeg(t, 6440f, 6500f)).coerceIn(0f, 1f)
    val isCal = t in 3930f..6480f
    val glyphScale = if (isCal) (0.35f + 0.65f * backOut(calLin(t, 3930f, 4070f), 1.5f)) * (1f - 0.6f * calSeg(t, 6400f, 6480f)) else 1f

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f / 0.82f).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer),
    ) {
        val mint = scheme.primaryContainer
        val blueColor = scheme.tertiary
        val glowColor = lerp(mint, blueColor, blue)
        val bubbleLight = lerp(mint, Color.White, 0.72f)
        val blueDot = lerp(blueColor, Color.White, 0.45f)

        Canvas(Modifier.fillMaxSize()) {
            val pw = size.width
            val ph = size.height
            val wf = pw * 0.775f
            val hf = wf / 2f
            val thick = wf * 0.078f
            val cx = pw / 2f
            val theta = Math.toRadians(thetaDeg.toDouble())
            val sinT = sin(theta).toFloat()
            val cosT = cos(theta).toFloat()
            val bandH = thick * sinT
            val flatFrac = (thetaDeg / 76f).coerceIn(0f, 1f)
            val yBottom = lerp(ph * 0.68f, ph * 0.85f - bandH, flatFrac)
            val d = wf * 8f
            val rollDeg = 1.3f * lean + 0.7f * settle * (1f - tiltOut)
            val label = signed(-1.1f * lean)

            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                n.save()
                // the left end rests on the camera puck, so the phone leans about its right-hand contact point
                n.rotate(rollDeg, cx + wf / 2f, yBottom)

                val left = cx - wf / 2f

                // the face of the phone, flat, and the perspective transform that tips it back
                val src = floatArrayOf(0f, 0f, wf, 0f, wf, hf, 0f, hf)
                val dst = FloatArray(8)
                fun project(i: Int, u: Float, v: Float) {
                    val depth = d + (-v) * sinT
                    val k = d / depth
                    dst[i * 2] = cx + u * k
                    dst[i * 2 + 1] = yBottom + v * cosT * k
                }
                project(0, -wf / 2f, -hf)
                project(1, wf / 2f, -hf)
                project(2, wf / 2f, 0f)
                project(3, -wf / 2f, 0f)
                val m = android.graphics.Matrix()
                m.setPolyToPoly(src, 0, dst, 0, 4)

                if (thetaDeg > 1f) {
                    // soft contact shadow under the phone and its puck
                    n.save()
                    val shY = yBottom + bandH + wf * 0.012f
                    n.translate(cx - wf * 0.06f, shY)
                    n.scale(1f, 0.07f)
                    p.shader = RadialGradient(0f, 0f, wf * 0.5f, intArrayOf(withAlpha(0xFF000000.toInt(), (110 * flatFrac).roundToInt()), 0), null, Shader.TileMode.CLAMP)
                    n.drawCircle(0f, 0f, wf * 0.5f, p)
                    p.shader = null
                    n.restore()

                    // the camera puck under the left end
                    if (lean > 0.02f) {
                        val pTop = yBottom + bandH * 0.4f
                        val pBottom = yBottom + bandH + wf * 0.03f * lean
                        p.color = 0xFF9FA5BB.toInt()
                        n.drawRoundRect(RectF(left + wf * 0.075f, pTop, left + wf * 0.265f, pBottom), wf * 0.014f, wf * 0.014f, p)
                        p.color = 0xFFC6CBDB.toInt()
                        n.drawRoundRect(RectF(left + wf * 0.085f, pBottom - wf * 0.012f * lean - 1f, left + wf * 0.255f, pBottom), wf * 0.012f, wf * 0.012f, p)
                    }

                    // the body: the outline of the face extruded downwards, so the back and the face are one solid piece
                    val outline = android.graphics.Path().apply {
                        addRoundRect(RectF(0f, 0f, wf, hf), hf * 0.13f, hf * 0.13f, android.graphics.Path.Direction.CW)
                        transform(m)
                    }
                    val layers = max(1, (bandH / 1.5f).toInt())
                    p.color = 0xFF7E849A.toInt()
                    n.save()
                    n.translate(0f, bandH)
                    n.drawPath(outline, p)
                    n.restore()
                    p.shader = LinearGradient(
                        left, 0f, left + wf, 0f,
                        intArrayOf(0xFF9097AD.toInt(), 0xFFC4C9D8.toInt(), 0xFFD9DDE8.toInt(), 0xFFB1B7CA.toInt(), 0xFF8F95AB.toInt()),
                        floatArrayOf(0f, 0.25f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP,
                    )
                    for (k in layers - 1 downTo 0) {
                        n.save()
                        n.translate(0f, bandH * k / layers)
                        n.drawPath(outline, p)
                        n.restore()
                    }
                    p.shader = null
                }

                n.concat(m)
                drawFace(
                    n, wf, hf,
                    FaceState(
                        label, glow, dim, liquidA, wave, blue, ((1f - calSeg(t, 2250f, 2400f)) + calSeg(t, 6850f, 6900f)).coerceIn(0f, 1f),
                        dotOff, ringOff, ghostA, settle,
                        mint.toArgb(), bubbleLight.toArgb(), blueDot.toArgb(),
                    ),
                    glowColor.toArgb(),
                )
                n.restore()
            }
        }

        // the reset / calibrate icon above the phone
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val diameter = maxWidth * 0.19f
            Box(
                Modifier
                    .offset(maxWidth / 2 - diameter / 2, maxHeight * 0.465f - diameter / 2)
                    .size(diameter)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = (iconScale * 2f).coerceIn(0f, 1f)
                    }
                    .shadow(6.dp, CircleShape)
                    .background(lerp(mint, blueColor, calMix), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (isCal) R.drawable.lv_calibrate else R.drawable.lv_reset),
                    contentDescription = null,
                    tint = lerp(scheme.onPrimaryContainer, scheme.onTertiary, calMix),
                    modifier = Modifier.size(diameter * 0.52f * glyphScale.coerceAtLeast(0.01f)),
                )
            }
        }
    }
}

/**
 * The phone's face in its own flat coordinates: a spirit level with a liquid and a cookie that slides off its outline,
 * which melts into a glowing dot (ringed, and turning blue while calibrating) once the phone lies flat.
 */
private fun drawFace(n: android.graphics.Canvas, w: Float, h: Float, s: FaceState, glowColor: Int) {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val r = h * 0.13f
    val white = 0xFFFFFFFF.toInt()

    // the camera humps peeking over the top edge
    if (s.tabsA > 0.01f) {
        p.color = 0xFFB9BFD2.toInt()
        p.alpha = (255 * s.tabsA.coerceIn(0f, 1f)).roundToInt()
        n.drawRoundRect(RectF(w * 0.26f, -h * 0.018f, w * 0.34f, h * 0.03f), h * 0.01f, h * 0.01f, p)
        n.drawRoundRect(RectF(w * 0.395f, -h * 0.018f, w * 0.54f, h * 0.03f), h * 0.01f, h * 0.01f, p)
        p.alpha = 255
    }

    // rim and screen
    p.color = 0xFFB9BFD2.toInt()
    n.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
    val inset = h * 0.02f
    val screen = RectF(inset, inset, w - inset, h - inset)
    p.color = 0xFF020F0C.toInt()
    n.drawRoundRect(screen, r - inset, r - inset, p)

    n.save()
    val clip = android.graphics.Path().apply { addRoundRect(screen, r - inset, r - inset, android.graphics.Path.Direction.CW) }
    n.clipPath(clip)

    // the liquid: a darker wave behind the bright one, dimming as the screen melts away
    fun wavePath(base: Float, amp: Float, phase: Float): android.graphics.Path {
        val path = android.graphics.Path()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, base + amp * sin((x / w + phase) * 2f * PI.toFloat()))
            x += w / 40f
        }
        path.lineTo(w, h)
        path.close()
        return path
    }
    val back = lerpInt(s.mint, 0xFF000000.toInt(), 0.45f)
    val dimmed = lerpInt(s.mint, 0xFF000000.toInt(), 0.5f)
    p.color = lerpInt(back, dimmed, s.dim)
    p.alpha = (255 * s.liquidA).roundToInt()
    n.drawPath(wavePath(h * 0.475f, h * 0.026f, -s.wave + 0.35f), p)
    p.color = lerpInt(s.mint, dimmed, s.dim)
    p.alpha = (255 * s.liquidA).roundToInt()
    n.drawPath(wavePath(h * 0.50f, h * 0.022f, s.wave), p)
    p.alpha = 255

    // the reading
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD9FFF3.toInt()
        alpha = (255 * (1f - s.glow * 0.92f)).roundToInt()
        textSize = h * 0.2f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    n.drawText(s.label, w / 2f, h * 0.335f, text)

    val cy = h * (0.66f + s.glow * (0.08f - 0.15f * s.settle))
    val dotX = w / 2f + w * 0.045f * s.dotOff
    val ringX = w / 2f + w * 0.045f * s.ringOff
    val half0 = h * 0.158f

    if (s.glow > 0.01f) {
        // a soft halo and the faint cross hair through the dot
        p.shader = RadialGradient(ringX, cy, h * 0.42f, intArrayOf(withAlpha(glowColor, (210 * s.glow).roundToInt()), withAlpha(glowColor, 0)), null, Shader.TileMode.CLAMP)
        n.drawCircle(ringX, cy, h * 0.42f, p)
        p.shader = null
        p.color = white
        p.alpha = (26 * s.glow).roundToInt()
        n.drawRect(RectF(0f, cy - h * 0.003f, w, cy + h * 0.003f), p)
        n.drawRect(RectF(ringX - h * 0.003f, 0f, ringX + h * 0.003f, h), p)
        p.alpha = 255
    }

    // the outline the cookie started in; it turns into the ring around the glowing dot
    val ghostX = lerpF(w / 2f, ringX, s.glow)
    val gHalf = lerpF(half0 * 1.14f, h * 0.28f, s.glow)
    val gCorner = lerpF(h * 0.075f, gHalf, s.glow)
    val brightBlue = lerpInt(s.blueC, white, 0.5f)
    if (s.ghostA > 0.01f) {
        val rect = RectF(ghostX - gHalf, cy - gHalf, ghostX + gHalf, cy + gHalf)
        if (s.glow > 0.01f) {
            p.color = glowColor
            p.alpha = (255 * 0.22f * s.glow * s.ghostA).roundToInt()
            n.drawRoundRect(rect, gCorner, gCorner, p)
        }
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = h * 0.012f
        val outline = lerpInt(lerpInt(back, glowColor, s.glow), brightBlue, s.blue)
        p.color = outline
        p.alpha = (255 * s.ghostA * lerpF(0.9f, lerpF(0.35f, 1f, s.blue), s.glow)).roundToInt()
        n.drawRoundRect(rect, gCorner, gCorner, p)
        p.style = android.graphics.Paint.Style.FILL
        p.alpha = 255
    }

    // the cookie: a rounded square that melts into a dot
    val half = lerpF(half0, h * 0.18f, s.glow)
    val corner = lerpF(h * 0.075f, half, s.glow)
    p.color = lerpInt(s.bubble, s.blueC, s.blue)
    n.drawRoundRect(RectF(dotX - half, cy - half, dotX + half, cy + half), corner, corner, p)
    if (s.blue > 0.3f) {
        p.color = 0xFF0B4D63.toInt()
        p.alpha = (255 * ((s.blue - 0.3f) / 0.7f)).roundToInt()
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = h * 0.011f
        p.strokeCap = android.graphics.Paint.Cap.ROUND
        n.drawLine(dotX - half * 0.30f, cy - h * 0.004f, dotX - half * 0.08f, cy - h * 0.004f, p)
        n.drawLine(dotX + half * 0.08f, cy - h * 0.004f, dotX + half * 0.34f, cy - h * 0.004f, p)
        p.style = android.graphics.Paint.Style.FILL
        p.alpha = 255
    }

    // the front camera, where the liquid meets the left edge
    p.color = 0xFF000000.toInt()
    p.alpha = (255 * (1f - 0.4f * s.glow)).roundToInt()
    n.drawCircle(w * 0.058f, h * 0.50f, h * 0.026f, p)
    p.alpha = 255
    n.restore()
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

private fun lerpInt(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = (((a shr shift) and 0xFF) + ((((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t)).roundToInt().coerceIn(0, 255)
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
