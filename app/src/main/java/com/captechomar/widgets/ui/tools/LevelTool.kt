package com.captechomar.widgets.ui.tools

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.SoundPool
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.drawscope.rotate as rotateScope
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
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
private const val OUTLINE = 0.405f // width of the outline shape
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

/** Shared with the tool header, which is retitled while flip to align is on. */
object LevelFlip {
    var on by mutableStateOf(false)
}

/** lub-dub: two short pulses, the second a little softer */
private fun heartbeat(v: Vibrator?, strong: Boolean = false) {
    v ?: return
    try {
        val amp = if (strong) intArrayOf(0, 255, 0, 255) else intArrayOf(0, 255, 0, 170)
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 38, 70, 46), amp, -1))
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    var flipMode by remember { mutableStateOf(prefs.getBoolean("level_flip", false)) }
    var volumeShown by remember { mutableStateOf(true) }
    var volume by remember { mutableFloatStateOf(prefs.getFloat("level_volume", 0.8f)) }
    var torch by remember { mutableStateOf(prefs.getBoolean("level_torch", false)) }
    SideEffect { LevelFlip.on = flipMode }
    DisposableEffect(Unit) { onDispose { LevelFlip.on = false } }
    var awake by remember { mutableStateOf(prefs.getBoolean("level_awake", true)) }
    var frozen by remember { mutableStateOf<FloatArray?>(null) } // the readings held by the lock
    var calRoll by remember { mutableFloatStateOf(prefs.getFloat("level_cal_roll", 0f)) }
    var calPitch by remember { mutableFloatStateOf(prefs.getFloat("level_cal_pitch", 0f)) }
    var calFlatSet by remember { mutableStateOf(prefs.getBoolean("level_calibrated", false)) }
    var calUp by remember { mutableFloatStateOf(prefs.getFloat("level_cal_up", 0f)) }
    var calUpSet by remember { mutableStateOf(prefs.getBoolean("level_calibrated_up", false)) }
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
    // standing up: the phone is read in the screen's own frame (so it follows the display rotation) and the tilt is how far
    // its nearest edge is from horizontal, like a spirit level held against a wall or a picture frame
    val rotation = view.display?.rotation ?: 0
    val sx = when (rotation) { 1 -> -smoothed[1]; 2 -> -smoothed[0]; 3 -> smoothed[1]; else -> smoothed[0] }
    val sy = when (rotation) { 1 -> smoothed[0]; 2 -> -smoothed[1]; 3 -> -smoothed[0]; else -> smoothed[1] }
    val upAngle = Math.toDegrees(atan2(sx.toDouble(), sy.toDouble())).toFloat()
    val edgeTurns = (upAngle / 90f).roundToInt()
    val liveUp = upAngle - 90f * edgeTurns
    val tubeRotation = 90f * edgeTurns

    // flat on a surface or standing up, with some give so it doesn't flip back and forth around the boundary
    val flatness = abs(smoothed[2]) / g
    val uprightState = remember { mutableStateOf(false) }
    val upright = if (uprightState.value) flatness < 0.78f else flatness < 0.62f
    SideEffect { uprightState.value = upright }
    val calibrated = if (upright) calUpSet else calFlatSet

    val rawRoll = frozen?.get(0) ?: liveRoll
    val rawPitch = frozen?.get(1) ?: livePitch
    val rawUp = frozen?.getOrNull(2) ?: liveUp
    // standing up there is only one tilt, so the shape slides along a line (to the high side, like a bubble)
    val roll = if (upright) rawUp - calUp else rawRoll - calRoll
    val pitch = if (upright) 0f else rawPitch - calPitch
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

    // flip to align: sounds, heartbeat vibration and the flashlight all follow the zone, so the screen can face down
    val sounds = remember {
        SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
    }
    val soundIds = remember { intArrayOf(sounds.load(context, R.raw.flip_line, 1), sounds.load(context, R.raw.flip_circle, 1), sounds.load(context, R.raw.flip_level, 1)) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    val camera = remember { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
    val torchId = remember {
        try {
            camera?.cameraIdList?.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        } catch (_: Throwable) {
            null
        }
    }
    fun setTorch(on: Boolean) {
        try {
            torchId?.let { camera?.setTorchMode(it, on) }
        } catch (_: Throwable) {
        }
    }
    DisposableEffect(flipMode) { onDispose { setTorch(false) } }

    val lastZone = remember { arrayOf(Zone.NONE) }
    val volumeNow = rememberUpdatedState(volume)
    LaunchedEffect(zone, haptic, flipMode, torch) {
        val before = lastZone[0]
        lastZone[0] = zone
        if (flipMode) {
            val flash = torch && torchId != null
            try {
                when (zone) {
                    Zone.SLOW, Zone.FAST -> {
                        val gap = if (zone == Zone.SLOW) 1237L else 510L
                        val id = if (zone == Zone.SLOW) soundIds[0] else soundIds[1]
                        while (true) {
                            sounds.play(id, volumeNow.value, volumeNow.value, 1, 0, 1f)
                            heartbeat(vibrator)
                            if (flash) {
                                setTorch(true)
                                delay(90)
                                setTorch(false)
                                delay(gap - 90)
                            } else delay(gap)
                        }
                    }
                    Zone.LEVEL -> {
                        sounds.play(soundIds[2], volumeNow.value, volumeNow.value, 1, 0, 1f)
                        heartbeat(vibrator, strong = true)
                        if (flash) setTorch(true)
                        awaitCancellation()
                    }
                    Zone.NONE -> Unit
                }
            } finally {
                if (flash) setTorch(false)
            }
            return@LaunchedEffect
        }
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
        if (upright) {
            calUp = liveUp
            calUpSet = true
            prefs.edit().putFloat("level_cal_up", liveUp).putBoolean("level_calibrated_up", true).apply()
        } else {
            calRoll = liveRoll
            calPitch = livePitch
            calFlatSet = true
            prefs.edit().putFloat("level_cal_roll", liveRoll).putFloat("level_cal_pitch", livePitch).putBoolean("level_calibrated", true).apply()
        }
        if (haptic) strongTick(vibrator)
    }

    fun resetCalibration() {
        if (upright) {
            calUp = 0f
            calUpSet = false
            prefs.edit().putFloat("level_cal_up", 0f).putBoolean("level_calibrated_up", false).apply()
        } else {
            calRoll = 0f
            calPitch = 0f
            calFlatSet = false
            prefs.edit().putFloat("level_cal_roll", 0f).putFloat("level_cal_pitch", 0f).putBoolean("level_calibrated", false).apply()
        }
    }

    // the colours: mint when moving, blue when level, dimmed while the readings are locked
    val centre = lerp(lerp(scheme.primaryContainer, scheme.tertiary, levelF), Color.Black, 0.42f * lockF)
    val fillColor = lerp(lerp(centre, Color.White, 0.65f), scheme.primary, lockF)
    val ringColor = lerp(lerp(centre, Color.Black, 0.72f), lerp(centre, Color.White, 0.45f), levelF)
    val inkColor = lerp(centre, Color.Black, 0.65f)
    val textColor = lerp(centre, Color.White, 0.88f)
    val barBg = lerp(scheme.secondaryContainer, scheme.primaryContainer, levelF)
    val barFg = lerp(scheme.onSecondaryContainer, scheme.onPrimaryContainer, levelF)
    val flipBase = if (flipMode) Color(0xFF6FD6FF) else scheme.primary
    val flipBg = lerp(flipBase, lerp(flipBase, Color.White, 0.6f), levelF)

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

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // the card runs from just under the header down to the option bar, which sits alone below it
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(36.dp)).pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > 50f) volumeShown = false else if (drag < -50f) volumeShown = true
                    },
                ) { _, amount -> drag += amount }
            },
        ) {
            // standing up with the screen turned a quarter away from the world (rotation locked, phone on its side) the
            // text and the shape are laid out for the sideways card and turned back so they read the right way up
            val sideways = upright && abs(edgeTurns) == 1
            val w = if (sideways) maxHeight else maxWidth
            val h = if (sideways) maxWidth else maxHeight
            val cx = w / 2
            val landscape = w > h
            // standing up, the shape sits in the lower part and the horizon crosses above it
            val cy = if (upright) h * (if (landscape) 0.76f else 0.62f) else h / 2
            val outlineSize = if (upright) minOf(w * 0.52f, h * 0.34f) else w * OUTLINE
            val fillSize = outlineSize * FILL
            val boxSize = fillSize * BOX

            // the background: a drop of colour with ripples spreading from it
            Canvas(Modifier.fillMaxSize()) {
              if (upright) {
                // a liquid surface that stays level while the phone turns: dark above, the bright fill below, with soft bands between
                val vw = w.toPx()
                val vh = h.toPx()
                val left = (size.width - vw) / 2
                val top = (size.height - vh) / 2
                val hy = top + vh * (if (landscape) 0.48f else 0.39f)
                val edge = lerp(centre, Color.Black, 0.55f)
                rotateScope(tubeRotation, Offset(size.width / 2, size.height / 2)) {
                    drawRect(
                        Brush.verticalGradient(listOf(lerp(centre, Color.Black, 0.88f), edge), startY = top, endY = top + (hy - top) * 1.15f),
                        topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(vw, vh),
                    )
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.5f), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f),
                            startX = left, endX = left + vw,
                        ),
                        topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(vw, vh),
                    )
                    rotateScope(roll, Offset(size.width / 2, hy)) {
                        val len = maxOf(size.width, size.height) * 3f
                        val amp = 3.6.dp.toPx()
                        val step = 6.dp.toPx()
                        fun surface(offset: Float, phase: Float): Path = Path().apply {
                            var x = -len
                            moveTo(x, hy + offset)
                            while (x <= len) {
                                lineTo(x, hy + offset + amp * sin(x / (vw * 0.3f) * 2f * PI.toFloat() + phase))
                                x += step
                            }
                            lineTo(len, hy + len)
                            lineTo(-len, hy + len)
                            close()
                        }
                        val ph = wave * 2f * PI.toFloat()
                        drawPath(surface(-26.dp.toPx(), ph), lerp(edge, centre, 0.28f))
                        drawPath(surface(-13.dp.toPx(), -ph + 1.7f), lerp(edge, centre, 0.55f))
                        drawPath(surface(0f, ph + 3.3f), centre)
                    }
                }
              } else {
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
            }

            Box(Modifier.requiredSize(w, h).align(Alignment.Center).graphicsLayer { rotationZ = if (upright) tubeRotation else 0f }) {
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
            // while not level the readouts are dark, the numbers darker than their labels; level brings back the light text
            val flatLabel = lerp(lerp(centre, Color.Black, 0.5f), textColor, levelF)
            val flatValue = lerp(lerp(centre, Color.Black, 0.82f), textColor, levelF)
            val big = (w.value * 0.07f).sp
            val small = (w.value * 0.036f).sp
            if (upright) {
                val nf = minOf(w.value, h.value) * 0.28f
                androidx.compose.material3.Text(
                    signed(-roll),
                    color = textColor,
                    fontSize = nf.sp,
                    lineHeight = nf.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = h * (if (landscape) 0.42f else 0.19f) - (nf * 0.5f).dp),
                )
            } else Readout(Modifier.offset(cx - 75.dp, cy - w * 0.36f - 45.dp), signed(roll), "Roll", big, small, flatLabel, labelFirst = true, valueColor = flatValue)
            for ((phi, value, label) in (if (upright) emptyList() else listOf(Triple(133.0, signed(pitch), "Pitch"), Triple(47.0, signed(incline), "Incline")))) {
                val r = Math.toRadians(phi)
                Readout(
                    Modifier
                        .offset(cx + w * (0.34f * cos(r)).toFloat() - 75.dp, cy + w * (0.34f * sin(r)).toFloat() - 45.dp)
                        .rotate((phi - 90.0).toFloat()),
                    value, label, big, small, flatLabel, labelFirst = false, valueColor = flatValue,
                )
            }

            // the beep volume floats over the card while flip to align is on; swipe the card down to hide it and up to bring it back
            val volF by animateFloatAsState(if (flipMode && volumeShown) 1f else 0f, tween(280), label = "volF")
            if (volF > 0.01f) Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (1f - volF) * (size.height + 24.dp.toPx())
                        alpha = volF
                    }
                    .clip(RoundedCornerShape(36.dp))
                    .background(barBg)
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.lv_speaker), contentDescription = "Beep volume", tint = barFg, modifier = Modifier.size(26.dp))
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = {
                        prefs.edit().putFloat("level_volume", volume).apply()
                        sounds.play(soundIds[1], volume, volume, 1, 0, 1f)
                    },
                    thumb = { Box(Modifier.size(width = 5.dp, height = 34.dp).clip(CircleShape).background(barFg)) },
                )
            }
            }
        }

        // the option bar and the flip-to-align button
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(barBg).padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BarButton(if (locked) R.drawable.lv_lock else R.drawable.lv_unlock, "Lock readings", locked, barFg, {
                    press()
                    frozen = if (locked) null else floatArrayOf(liveRoll, livePitch, liveUp)
                }, { sheet = 0 })
                if (flipMode) BarButton(if (torch) R.drawable.lv_torch_on else R.drawable.lv_torch_off, "Flashlight", torch, barFg, {
                    press()
                    torch = !torch
                    prefs.edit().putBoolean("level_torch", torch).apply()
                }, { sheet = 5 })
                else BarButton(R.drawable.lv_vibrate, "Haptic feedback", haptic, barFg, {
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
                    .combinedClickable(onClick = {
                        press()
                        flipMode = !flipMode
                        prefs.edit().putBoolean("level_flip", flipMode).apply()
                    }, onLongClick = { sheet = 4 }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (flipMode) R.drawable.lv_flip_on else R.drawable.lv_flip), contentDescription = "Flip to align",
                    tint = if (flipMode) Color(0xFF00344A) else scheme.onPrimary, modifier = Modifier.size(28.dp),
                )
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
            "Level your device perfectly without needing to look at the screen. Place your phone face down and let precise audio and vibration feedback guide your adjustments.",
            "Start Using", { sheet = -1 }, {
                if (!flipMode) {
                    flipMode = true
                    prefs.edit().putBoolean("level_flip", true).apply()
                }
                sheet = -1
            },
            extra = { FlipAnimation() },
        )
        5 -> InfoSheet(
            R.drawable.lv_torch_on, "Flashlight",
            "Flash the light with every beat while you align, and keep it on once the phone is level. Handy with the screen facing down.",
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
private fun Readout(modifier: Modifier, value: String, label: String, big: TextUnit, small: TextUnit, color: Color, labelFirst: Boolean, valueColor: Color = color) {
    Box(modifier.size(150.dp, 90.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val l = @Composable { Text(label, fontSize = small, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.5f), textAlign = TextAlign.Center) }
            val v = @Composable { Text(value, fontSize = big, fontWeight = FontWeight.Bold, color = valueColor, textAlign = TextAlign.Center, maxLines = 1) }
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

    val lean = (calSeg(t, 2100f, 2550f) - calSeg(t, 6740f, 6960f)).coerceIn(0f, 1f)
    val tiltIn = calLin(t, 2200f, 2720f)
    // lifting the phone takes as long as putting it down (about half a second) and eases out slowly at the end
    val tiltOut = 1f - (1f - calLin(t, 6480f, 7000f)).pow(1.7f)
    val settle = calSeg(t, 2800f, 3600f)
    val thetaDeg = (76f * (1f - (1f - tiltIn).pow(1.7f)) + 6f * settle) * (1f - tiltOut)
    val glow = (calSeg(t, 2450f, 2720f) - calSeg(t, 6800f, 6950f)).coerceIn(0f, 1f)
    val dim = calSeg(t, 2450f, 2560f) * (1f - calSeg(t, 6900f, 6980f))
    val liquidA = (1f - calSeg(t, 2560f, 2740f) + calSeg(t, 6880f, 6960f)).coerceIn(0f, 1f)
    val blue = (calSeg(t, 3940f, 4040f) - calSeg(t, 6440f, 6500f)).coerceIn(0f, 1f)

    // the cookie slides off its outline while the phone leans, then glides back once the button is pressed
    val slosh = calSeg(t, 2200f, 2650f)
    val glide = 1f - backOut(calLin(t, 4050f, 4480f), 1.2f)
    val dotOff = slosh * glide
    val ringOff = slosh * (1f - calSeg(t, 3930f, 4050f))
    val ghostA = (slosh * (1f - glow) + glow) * (1f - calSeg(t, 6860f, 6980f))

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
                        label, glow, dim, liquidA, wave, blue, ((1f - calSeg(t, 2250f, 2400f)) + calSeg(t, 6930f, 7000f)).coerceIn(0f, 1f),
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

// One loop is 6.85 s and starts with the phone being picked up (the bubbles shrink as it lifts and flips back to face up).
private const val FLIP_LOOP = 6850f
private const val FLIP_LEAD = 650f

/**
 * The flip-to-align explainer, timed from the reference recording (one 7 s loop): the phone shows its level screen, flips
 * over (0.3 s), tips flat, the flashlight comes on, the sound and vibration bubbles pop out and shake, then the phone is
 * lifted, rolled and flipped back.
 */
@Composable
private fun FlipAnimation() {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberInfiniteTransition(label = "flip")
    val ms by loop.animateFloat(0f, 7000f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t")
    val wave by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "flipwave")
    val s = ms / 1000f

    val yawIn = calSeg(s, 1.32f, 1.62f)
    val yawOut = calSeg(s, 6.37f, 6.67f)
    val tiltIn = 1f - (1f - calLin(s, 2.52f, 3.2f)).pow(3)
    val tiltOutF = calSeg(s, 6.27f, 6.62f)
    val tilt = (tiltIn * (1f - tiltOutF)).coerceIn(0f, 1f)
    val roll = 24f * calSeg(s, 6.27f, 6.55f) * (1f - calSeg(s, 6.6f, 6.95f))
    val flash = (calLin(s, 3.22f, 3.27f) - calLin(s, 6.2f, 6.24f)).coerceIn(0f, 1f)
    val label = if (s < 4f) signed(-0.5f * yawIn) else signed(-0.5f * (1f - calSeg(s, 6.67f, 6.9f)))
    val popA = backOut(calLin(s, 3.42f, 3.70f), 1.6f) * (1f - calLin(s, 6.25f, 6.33f))
    val popB = backOut(calLin(s, 3.45f, 3.73f), 1.6f) * (1f - calLin(s, 6.26f, 6.34f))
    val shake = if (s in 3.7f..6.25f) 1f else 0f
    val mint = scheme.primaryContainer

    Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer)) {
        Canvas(Modifier.fillMaxSize()) {
            val pw = size.width
            val ph = size.height
            val h = ph * 0.93f
            val w = h * 0.5f
            val thick = w * 0.082f
            val lift = w * 0.03f
            val dist = h * 5.3f
            val alpha = Math.toRadians(66.0 * tilt).toFloat()
            val sa = sin(alpha)
            val ca = cos(alpha)
            val phi = PI.toFloat() * (yawIn + yawOut)
            val sp = sin(phi)
            val cp = cos(phi)
            val cx = pw / 2f
            val cy = lerp(ph * 0.5f, ph * 0.615f, tilt)

            fun project(out: FloatArray, i: Int, x: Float, y: Float, z: Float) {
                val x1 = x * cp + z * sp
                val z1 = -x * sp + z * cp
                val y2 = y * ca - z1 * sa
                val z2 = y * sa + z1 * ca
                val k = dist / (dist - z2)
                out[i * 2] = cx + x1 * k
                out[i * 2 + 1] = cy + y2 * k
            }
            val xs = floatArrayOf(-w / 2f, w / 2f, w / 2f, -w / 2f)
            val ys = floatArrayOf(-h / 2f, -h / 2f, h / 2f, h / 2f)
            fun planeAt(z: Float): FloatArray {
                val out = FloatArray(8)
                for (i in 0..3) project(out, i, xs[i], ys[i], z)
                return out
            }
            val front = planeAt(thick / 2f)
            val back = planeAt(-thick / 2f)
            val frontShown = cp >= 0f
            val near = if (frontShown) front else back
            val far = if (frontShown) back else front
            val srcFront = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
            val srcBack = floatArrayOf(w, 0f, 0f, 0f, 0f, h, w, h) // the back is drawn as seen from behind, camera bar top left
            val rail = intArrayOf(0xFF8F96AD.toInt(), 0xFFB9BFD2.toInt(), 0xFFDDE1EC.toInt(), 0xFFAEB4C8.toInt(), 0xFF8A91A8.toInt())

            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val m = android.graphics.Matrix()
                n.save()
                n.rotate(roll, cx, cy)

                if (tilt > 0.02f) {
                    n.save()
                    n.translate(cx, cy + h * 0.5f * ca * 0.9f + h * 0.03f)
                    n.scale(1f, 0.1f)
                    p.shader = RadialGradient(0f, 0f, w * 0.62f, intArrayOf(withAlpha(0xFF000000.toInt(), (110 * tilt).roundToInt()), 0), null, Shader.TileMode.CLAMP)
                    n.drawCircle(0f, 0f, w * 0.62f, p)
                    p.shader = null
                    n.restore()
                }

                fun withPlane(plane: FloatArray, srcPts: FloatArray, block: () -> Unit) {
                    m.setPolyToPoly(srcPts, 0, plane, 0, 4)
                    n.save()
                    n.concat(m)
                    block()
                    n.restore()
                }

                // the camera module stands proud of the back: layers from the back surface up to its top face
                val modPath = android.graphics.Path().apply { addRoundRect(RectF(w * 0.043f, h * 0.072f, w * 0.957f, h * 0.246f), h * 0.087f, h * 0.087f, android.graphics.Path.Direction.CW) }
                val baseZ = -thick / 2f
                val topPlane = planeAt(baseZ - lift)
                fun drawModule() {
                    // a soft shadow on the back under it, then the silver sides, then the top
                    for (k in 3 downTo 1) {
                        withPlane(back, srcBack) {
                            p.color = withAlpha(0xFF000000.toInt(), 20)
                            n.save()
                            n.translate(0f, h * 0.012f * k)
                            n.drawPath(modPath, p)
                            n.restore()
                        }
                    }
                    val steps = 6
                    val tmp = FloatArray(8)
                    p.color = 0xFF8C93AA.toInt()
                    for (j in 0..steps) {
                        val f = j / steps.toFloat()
                        for (q in 0..7) tmp[q] = back[q] + (topPlane[q] - back[q]) * f
                        withPlane(tmp, srcBack) { n.drawPath(modPath, p) }
                    }
                    withPlane(topPlane, srcBack) {
                        p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(0xFFA9AFC4.toInt(), 0xFFC6CCDD.toInt(), 0xFFB6BCD0.toInt()), null, Shader.TileMode.CLAMP)
                        n.drawPath(modPath, p)
                        p.shader = null
                        p.color = 0xFF161616.toInt()
                        n.drawRoundRect(RectF(w * 0.06f, h * 0.082f, w * 0.693f, h * 0.236f), h * 0.077f, h * 0.077f, p)
                        val ly = h * 0.159f
                        // the three lenses: a small blue one, a big black one with a highlight, and a squared one
                        p.color = 0xFF050507.toInt()
                        n.drawCircle(w * 0.19f, ly, w * 0.054f, p)
                        p.color = 0xFF1B2A78.toInt()
                        n.drawCircle(w * 0.178f, ly - w * 0.012f, w * 0.03f, p)
                        p.color = 0xFF050507.toInt()
                        n.drawCircle(w * 0.375f, ly, w * 0.084f, p)
                        p.shader = RadialGradient(w * 0.36f, ly - w * 0.03f, w * 0.05f, intArrayOf(0xFF6A6A6E.toInt(), 0), null, Shader.TileMode.CLAMP)
                        n.drawCircle(w * 0.375f, ly, w * 0.084f, p)
                        p.shader = null
                        p.color = 0xFF08090C.toInt()
                        n.drawRoundRect(RectF(w * 0.505f, ly - h * 0.039f, w * 0.614f, ly + h * 0.039f), w * 0.02f, w * 0.02f, p)
                        p.color = 0xFF1E2C80.toInt()
                        n.drawRoundRect(RectF(w * 0.512f, ly - h * 0.034f, w * 0.56f, ly - h * 0.005f), w * 0.015f, w * 0.015f, p)
                        // the two dots at the end of the bar; the upper one is the flashlight
                        val fx = w * 0.815f
                        val fy = h * 0.125f
                        if (flash > 0.01f) {
                            p.shader = RadialGradient(fx, fy, w * 0.3f, intArrayOf(withAlpha(0xFFFFEC70.toInt(), (200 * flash).roundToInt()), 0), null, Shader.TileMode.CLAMP)
                            n.drawCircle(fx, fy, w * 0.3f, p)
                            p.shader = null
                        }
                        p.color = lerpInt(0xFF4A4A4C.toInt(), 0xFFFFF08C.toInt(), flash)
                        n.drawCircle(fx, fy, w * 0.054f, p)
                        p.color = 0xFF4A4A4C.toInt()
                        n.drawCircle(fx, h * 0.188f, w * 0.054f, p)
                    }
                }

                // the volume rocker and power key stick out of one side edge
                fun drawButtons() {
                    p.color = 0xFF9CA3B8.toInt()
                    for ((a, b) in listOf(0.272f to 0.337f, 0.385f to 0.537f)) {
                        val bp = FloatArray(8)
                        project(bp, 0, w / 2f + w * 0.016f, -h / 2f + h * a, thick * 0.28f)
                        project(bp, 1, w / 2f + w * 0.016f, -h / 2f + h * a, -thick * 0.28f)
                        project(bp, 2, w / 2f + w * 0.016f, -h / 2f + h * b, -thick * 0.28f)
                        project(bp, 3, w / 2f + w * 0.016f, -h / 2f + h * b, thick * 0.28f)
                        val path = android.graphics.Path()
                        path.moveTo(bp[0], bp[1]); path.lineTo(bp[2], bp[3]); path.lineTo(bp[4], bp[5]); path.lineTo(bp[6], bp[7]); path.close()
                        n.drawPath(path, p)
                    }
                }
                val edgeFacing = -sp * ca > 0f

                if (frontShown) drawModule()
                if (!edgeFacing) drawButtons()

                // the body: the outline extruded from the far face to the near one
                val outline = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, w, h), w * 0.17f, w * 0.17f, android.graphics.Path.Direction.CW) }
                val minX = min(min(near[0], near[2]), min(near[4], near[6]))
                val maxX = max(max(far[0], far[2]), max(far[4], far[6])).coerceAtLeast(minX + 1f)
                p.shader = LinearGradient(minX, 0f, maxX, 0f, rail, floatArrayOf(0f, 0.25f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP)
                val layers = 18
                val dst = FloatArray(8)
                for (j in 0..layers) {
                    val f = j / layers.toFloat()
                    for (q in 0..7) dst[q] = far[q] + (near[q] - far[q]) * f
                    withPlane(dst, srcFront) { n.drawPath(outline, p) }
                }
                p.shader = null
                if (edgeFacing) drawButtons()

                // the face turned towards us
                withPlane(near, if (frontShown) srcFront else srcBack) {
                    if (frontShown) drawFlipFront(n, w, h, label, wave, mint.toArgb(), lerp(mint, Color.White, 0.72f).toArgb())
                    else drawFlipBack(n, w, h)
                }
                if (!frontShown) drawModule()
                n.restore()
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = maxWidth * 0.143f
            val sec = s
            val shX = shake * maxWidth * 0.006f * sin(2f * PI.toFloat() * 11f * sec)
            val shY = shake * maxWidth * 0.002f * sin(2f * PI.toFloat() * 17f * sec + 1f)
            val shR = shake * 9f * sin(2f * PI.toFloat() * 11f * sec + 0.8f)
            for ((idx, cfg) in listOf(Triple(R.drawable.lv_speaker_fill, 0.386f, popA), Triple(R.drawable.lv_vibrate, 0.614f, popB)).withIndex()) {
                val (icon, yf, pop) = cfg
                Box(
                    Modifier
                        .offset(maxWidth * 0.857f - d / 2 + (shX * (if (idx == 0) 1f else -1f)), maxHeight * yf - d / 2 + shY)
                        .size(d)
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                            alpha = (pop * 2f).coerceIn(0f, 1f)
                        }
                        .shadow(5.dp, CircleShape)
                        .background(mint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(icon), null, tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(d * 0.5f).graphicsLayer { rotationZ = if (idx == 1) shR else 0f },
                    )
                }
            }
        }
    }
}

private fun drawFlipFront(n: android.graphics.Canvas, w: Float, h: Float, label: String, wave: Float, mint: Int, bubble: Int) {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val r = w * 0.17f
    p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(0xFF8F96AD.toInt(), 0xFFC9CEDD.toInt(), 0xFF9AA1B7.toInt()), null, Shader.TileMode.CLAMP)
    n.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
    p.shader = null
    val rim = w * 0.016f
    p.color = 0xFF151515.toInt()
    n.drawRoundRect(RectF(rim, rim, w - rim, h - rim), r - rim, r - rim, p)
    val inset = w * 0.049f
    val screen = RectF(inset, inset, w - inset, h - inset)
    p.color = 0xFF020D0B.toInt()
    n.drawRoundRect(screen, r - inset, r - inset, p)
    n.save()
    n.clipPath(android.graphics.Path().apply { addRoundRect(screen, r - inset, r - inset, android.graphics.Path.Direction.CW) })
    fun wavePath(base: Float, amp: Float, phase: Float): android.graphics.Path {
        val path = android.graphics.Path()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, base + amp * sin((x / w + phase) * 2f * PI.toFloat()))
            x += w / 30f
        }
        path.lineTo(w, h)
        path.close()
        return path
    }
    p.color = 0xFF0A7060.toInt()
    n.drawPath(wavePath(h * 0.404f, h * 0.014f, -wave + 0.3f), p)
    p.color = mint
    n.drawPath(wavePath(h * 0.405f, h * 0.015f, wave), p)
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC6FFF0.toInt()
        textSize = w * 0.163f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    n.drawText(label, w / 2f, h * 0.23f, text)
    p.color = bubble
    val half = w * 0.153f
    n.drawRoundRect(RectF(w / 2f - half, h * 0.576f - half, w / 2f + half, h * 0.576f + half), w * 0.1f, w * 0.1f, p)
    p.color = 0xFF000000.toInt()
    n.drawCircle(w / 2f, h * 0.06f, w * 0.03f, p)
    n.restore()
}

private fun drawFlipBack(n: android.graphics.Canvas, w: Float, h: Float) {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val r = w * 0.17f
    p.shader = LinearGradient(
        0f, 0f, w * 0.55f, h * 1.05f,
        intArrayOf(0xFF8E94A7.toInt(), 0xFF6B7181.toInt(), 0xFF666B7B.toInt(), 0xFF595E6D.toInt()),
        floatArrayOf(0f, 0.22f, 0.6f, 1f), Shader.TileMode.CLAMP,
    )
    n.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
    p.shader = null
    p.style = android.graphics.Paint.Style.STROKE
    p.strokeWidth = w * 0.006f
    p.color = withAlpha(0xFFA8AEC2.toInt(), 120)
    n.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
    p.style = android.graphics.Paint.Style.FILL
}
