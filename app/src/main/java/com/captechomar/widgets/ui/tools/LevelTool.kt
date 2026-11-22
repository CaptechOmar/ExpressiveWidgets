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

private const val LOOP_MS = 7600f

/**
 * The calibration explainer: a phone lying on its camera bump reads a small tilt, is put flat on a surface and
 * glows, a reset icon pops up and turns into the calibrate icon, then the phone is picked up again. Loops.
 */
@Composable
private fun CalibrationAnimation() {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberInfiniteTransition(label = "calibration")
    val ms by loop.animateFloat(0f, LOOP_MS, infiniteRepeatable(tween(LOOP_MS.toInt(), easing = LinearEasing)), label = "ms")
    val wave by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wave")

    fun seg(a: Float, b: Float) = smooth((ms - a) / (b - a))

    val roll = (seg(1500f, 2100f) - seg(7200f, 7600f)).coerceIn(0f, 1f) // the phone settles onto its camera bump
    val flat = (seg(2100f, 2550f) - seg(7150f, 7600f)).coerceIn(0f, 1f) // the phone lies down (0 = seen from above)
    val glow = (seg(2250f, 2650f) - seg(7250f, 7600f)).coerceIn(0f, 1f) // the screen turns into a glowing dot
    val blue = (seg(4300f, 4700f) - seg(7200f, 7500f)).coerceIn(0f, 1f) // the glow and the icon turn blue

    // the icon: pops up, dips as it changes from reset to calibrate, and shrinks away before the phone is lifted
    val dip = if (ms in 4300f..4700f) sin(((ms - 4300f) / 400f) * PI.toFloat()) * 0.3f else 0f
    val iconScale = seg(2900f, 3300f) * (1f - dip) * (1f - seg(6900f, 7200f))
    val isBlue = ms in 4500f..7050f

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f / 0.82f).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer),
    ) {
        val mint = scheme.primaryContainer
        val blueColor = scheme.tertiary
        val glowColor = lerp(mint, blueColor, blue)
        val bubbleLight = lerp(mint, Color.White, 0.65f)
        val liquidDark = lerp(mint, Color.Black, 0.5f)

        Canvas(Modifier.fillMaxSize()) {
            val pw = size.width
            val ph = size.height
            val wf = pw * 0.78f
            val hf = wf / 2.1f
            val thick = wf * 0.055f
            val cx = pw / 2f
            val theta = Math.toRadians((76.0 * flat))
            val sinT = sin(theta).toFloat()
            val cosT = cos(theta).toFloat()
            val yBottom = lerp(ph * 0.68f, ph * 0.86f - thick * 0.97f, flat)
            val d = wf * 8f
            val rollDeg = 1.3f * roll
            val label = signed(-1.1f * roll)

            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                n.save()
                // the left end is held up by the camera bar, so the whole phone leans about its right-hand contact point
                n.rotate(rollDeg, cx + wf / 2f, yBottom)

                if (flat > 0.02f) {
                    val bandH = thick * sinT
                    // the camera bar under the left end
                    p.color = 0xFFA6ACC0.toInt()
                    n.drawRoundRect(
                        RectF(cx - wf / 2f + wf * 0.12f, yBottom + bandH - 4f, cx - wf / 2f + wf * 0.27f, yBottom + bandH + wf * 0.024f * flat),
                        10f, 10f, p,
                    )
                    // the near edge of the phone
                    p.shader = LinearGradient(
                        cx - wf / 2f, 0f, cx + wf / 2f, 0f,
                        intArrayOf(0xFF8E94A8.toInt(), 0xFFD6DBE8.toInt(), 0xFFB3B9CC.toInt(), 0xFF8E94A8.toInt()),
                        floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
                    )
                    n.drawRoundRect(RectF(cx - wf / 2f, yBottom - 3f, cx + wf / 2f, yBottom + bandH), 8f, 8f, p)
                    p.shader = null
                }

                // the face of the phone, drawn flat and then tipped back with a perspective transform
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
                n.concat(m)
                drawFace(
                    n, wf, hf, label, glow, roll, wave, blue,
                    mint.toArgb(), bubbleLight.toArgb(), liquidDark.toArgb(), glowColor.toArgb(),
                )
                n.restore()
            }
        }

        // the reset / calibrate icon above the phone
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val diameter = maxWidth * 0.19f
            Box(
                Modifier
                    .offset(maxWidth / 2 - diameter / 2, maxHeight * 0.46f - diameter / 2)
                    .size(diameter)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = (iconScale * 2f).coerceIn(0f, 1f)
                    }
                    .shadow(6.dp, CircleShape)
                    .background(if (isBlue) blueColor else mint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (isBlue) R.drawable.lv_calibrate else R.drawable.lv_reset),
                    contentDescription = null,
                    tint = if (isBlue) scheme.onTertiary else scheme.onPrimaryContainer,
                    modifier = Modifier.size(diameter * 0.52f),
                )
            }
        }
    }
}

/** The phone's face, in its own flat coordinates: a screen showing a spirit level, which turns into a glow once the phone lies flat. */
private fun drawFace(
    n: android.graphics.Canvas,
    w: Float,
    h: Float,
    label: String,
    glow: Float,
    roll: Float,
    wave: Float,
    blue: Float,
    mint: Int,
    bubbleLight: Int,
    liquidDark: Int,
    glowColor: Int,
) {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val r = h * 0.13f
    // rim and screen
    p.color = 0xFFB9BFD2.toInt()
    n.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
    val inset = h * 0.03f
    val screen = RectF(inset, inset, w - inset, h - inset)
    p.color = 0xFF020C0A.toInt()
    n.drawRoundRect(screen, r - inset, r - inset, p)

    n.save()
    val clip = android.graphics.Path().apply { addRoundRect(screen, r - inset, r - inset, android.graphics.Path.Direction.CW) }
    n.clipPath(clip)

    // the liquid, two waves, fading to nothing as the screen turns into a glow
    val liquidAlpha = 1f - smooth((glow - 0.45f) / 0.55f)
    fun wavePath(base: Float, amp: Float, phase: Float): android.graphics.Path {
        val path = android.graphics.Path()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, base + amp * sin((x / w * 1.3f + phase) * 2f * PI.toFloat()))
            x += w / 40f
        }
        path.lineTo(w, h)
        path.close()
        return path
    }
    val liquid = lerpInt(mint, liquidDark, glow)
    p.color = liquidDark
    p.alpha = (255 * liquidAlpha * 0.85f).roundToInt()
    n.drawPath(wavePath(h * 0.40f, h * 0.03f, wave + 0.3f), p)
    p.color = liquid
    p.alpha = (255 * liquidAlpha).roundToInt()
    n.drawPath(wavePath(h * 0.44f, h * 0.025f, -wave), p)
    p.alpha = 255

    // the reading
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD9FFF3.toInt()
        alpha = (255 * (1f - glow)).roundToInt()
        textSize = h * 0.2f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    n.drawText(label, w / 2f, h * 0.30f, text)

    // the bubble: a rounded square on the liquid that becomes a glowing dot
    val cy = lerpF(h * 0.66f, h * 0.52f, glow)
    val half = lerpF(h * 0.17f, h * 0.11f, glow)
    val corner = lerpF(h * 0.06f, half, glow)
    val bx = w / 2f + w * 0.02f * roll * (1f - glow)
    if (glow > 0.01f) {
        p.shader = RadialGradient(w / 2f, cy, h * (0.18f + 0.5f * glow), intArrayOf(withAlpha(glowColor, (200 * glow).roundToInt()), withAlpha(glowColor, 0)), null, Shader.TileMode.CLAMP)
        n.drawCircle(w / 2f, cy, h * (0.18f + 0.5f * glow), p)
        p.shader = null
    }
    if (roll > 0.01f && glow < 0.9f) {
        // the ghost of where the bubble sat before it slid
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = h * 0.012f
        p.color = liquidDark
        p.alpha = (255 * (1f - glow)).roundToInt()
        n.drawRoundRect(RectF(w / 2f - half, cy - half, w / 2f + half, cy + half), corner, corner, p)
        p.style = android.graphics.Paint.Style.FILL
        p.alpha = 255
    }
    p.color = lerpInt(bubbleLight, withAlpha(glowColor, 255), glow * 0.35f)
    n.drawRoundRect(RectF(bx - half, cy - half, bx + half, cy + half), corner, corner, p)

    // rings spreading from the glow once the phone is calibrating
    if (blue > 0.01f) {
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = h * 0.012f
        for (k in 0 until 3) {
            val ph = (wave + k / 3f) % 1f
            p.color = glowColor
            p.alpha = (255 * (1f - ph) * 0.7f * blue * glow).roundToInt()
            n.drawCircle(w / 2f, cy, half * (1.2f + 2.4f * ph), p)
        }
        p.style = android.graphics.Paint.Style.FILL
        p.alpha = 255
    }

    // the front camera
    p.color = 0xFF000000.toInt()
    p.alpha = (255 * (1f - glow)).roundToInt()
    n.drawCircle(w * 0.035f, h * 0.40f, h * 0.03f, p)
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
