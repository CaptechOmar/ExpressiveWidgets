package com.captechomar.widgets.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.RoundedPolygon
import com.captechomar.widgets.R
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DIAL_SHAPES: List<Pair<String, RoundedPolygon>> = listOf(
    "Classic" to MaterialShapes.Cookie12Sided,
    "Modern" to MaterialShapes.Circle,
    "Very Sunny" to MaterialShapes.VerySunny,
    "Pixel Circle" to MaterialShapes.PixelCircle,
    "Sunny" to MaterialShapes.Sunny,
    "Cookie" to MaterialShapes.Cookie9Sided,
    "Burst" to MaterialShapes.SoftBurst,
    "Flower" to MaterialShapes.Flower,
    "Clover" to MaterialShapes.Clover8Leaf,
)

/** What a corner of the compass can show. */
enum class CornerData(val label: String, val iconRes: Int) {
    EMPTY("Empty", R.drawable.cc_empty),
    ALTITUDE("Altitude", R.drawable.cc_altitude),
    INCLINE("Incline", R.drawable.cc_incline),
    LATITUDE("Latitude", R.drawable.cc_latitude),
    LONGITUDE("Longitude", R.drawable.cc_longitude),
    SPEED("Speed", R.drawable.cc_speed),
    PRESSURE("Pressure", R.drawable.cc_pressure),
    TIME("Time", R.drawable.cc_time),
    ;

    val needsLocation get() = this == LATITUDE || this == LONGITUDE || this == SPEED
}

/** How the compass looks and behaves, saved between visits. Corners are top left, top right, bottom left, bottom right. */
class CompassSettings(private val prefs: SharedPreferences) {
    var shape by mutableIntStateOf(prefs.getInt("compass_shape", 0))
    var redNorth by mutableStateOf(prefs.getBoolean("compass_red_north", true))
    var doubleArrow by mutableStateOf(prefs.getBoolean("compass_double_arrow", false))
    var numbers by mutableStateOf(prefs.getBoolean("compass_numbers", false))
    var fourPoints by mutableStateOf(prefs.getBoolean("compass_four_points", false))
    var vibrate by mutableStateOf(prefs.getBoolean("compass_vibrate", true))
    val corners = mutableStateListOf<CornerData>().also { list ->
        DEFAULT_CORNERS.forEachIndexed { i, d ->
            list += CornerData.entries.getOrNull(prefs.getInt("compass_corner_$i", d.ordinal)) ?: d
        }
    }

    fun change(block: CompassSettings.() -> Unit) {
        block()
        val e = prefs.edit()
            .putInt("compass_shape", shape).putBoolean("compass_red_north", redNorth).putBoolean("compass_double_arrow", doubleArrow)
            .putBoolean("compass_numbers", numbers).putBoolean("compass_four_points", fourPoints).putBoolean("compass_vibrate", vibrate)
        corners.forEachIndexed { i, d -> e.putInt("compass_corner_$i", d.ordinal) }
        e.apply()
    }

    fun reset() = change {
        shape = 0
        redNorth = true
        doubleArrow = false
        numbers = false
        fourPoints = false
        vibrate = true
        DEFAULT_CORNERS.forEachIndexed { i, d -> corners[i] = d }
    }

    private companion object {
        val DEFAULT_CORNERS = listOf(CornerData.ALTITUDE, CornerData.INCLINE, CornerData.LATITUDE, CornerData.LONGITUDE)
    }
}

@Suppress("DEPRECATION")
private fun vibratorOf(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

private fun tick(v: Vibrator?) {
    v ?: return
    try {
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(8, 90))
    } catch (_: Throwable) {
    }
}

private fun lastLocation(context: Context): Location? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Throwable) { null } }
        .maxByOrNull { it.time }
}

private fun dms(value: Double, positive: String, negative: String): String {
    val v = abs(value)
    val d = v.toInt()
    val m = ((v - d) * 60).toInt()
    val s = ((v - d) * 60 - m) * 60
    return "%d° %02d' %02d\" %s".format(d, m, s.roundToInt().coerceAtMost(59), if (value >= 0) positive else negative)
}

private const val DIAL_FRACTION = 0.845f

@Composable
fun CompassTool() {
    val context = LocalContext.current
    val (available, rotation) = rememberSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (!available) return Missing("compass")
    val (_, accel) = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    val (_, baro) = rememberSensor(Sensor.TYPE_PRESSURE)
    val settings = remember { CompassSettings(context.applicationContext.getSharedPreferences("tools", Context.MODE_PRIVATE)) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var heading by remember { mutableFloatStateOf(0f) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var location by remember { mutableStateOf(lastLocation(context)) }
    val vibrator = remember { vibratorOf(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)
        location = lastLocation(context)
    }

    val v = rotation.value
    LaunchedEffect(v) {
        if (v == null) return@LaunchedEffect
        val m = FloatArray(9)
        val r = FloatArray(9)
        val o = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(m, v)
        // held upright, the direction that counts is where the back of the phone points
        if (abs(m[8]) < 0.6f) SensorManager.remapCoordinateSystem(m, SensorManager.AXIS_X, SensorManager.AXIS_Z, r) else m.copyInto(r)
        SensorManager.getOrientation(r, o)
        val target = (Math.toDegrees(o[0].toDouble()).toFloat() + 360f) % 360f
        // take the short way round so the dial does not spin when crossing north
        var d = target - heading
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        heading = (heading + d * 0.3f + 360f) % 360f
    }

    // a vibration tick for each degree the dial turns; the 1 degree gap keeps sensor jitter silent
    var lastTick by remember { mutableFloatStateOf(heading) }
    var lastTickTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(heading) {
        var d = heading - lastTick
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        if (abs(d) >= 1f) {
            lastTick = heading
            val now = SystemClock.uptimeMillis()
            if (settings.vibrate && now - lastTickTime >= 25) {
                lastTickTime = now
                tick(vibrator)
            }
        }
    }

    // live location while a corner needs it
    val needsLocation = settings.corners.any { it.needsLocation || it == CornerData.ALTITUDE }
    DisposableEffect(hasPermission, needsLocation) {
        if (!hasPermission || !needsLocation) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                val old = location
                // a satellite fix wins over a network one for ten seconds
                if (old == null || l.provider == LocationManager.GPS_PROVIDER || l.time - old.time > 10_000) location = l
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val providers = if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        else listOf(LocationManager.NETWORK_PROVIDER)
        @SuppressLint("MissingPermission")
        for (p in providers) {
            try {
                if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
            } catch (_: Throwable) {
            }
        }
        onDispose { lm.removeUpdates(listener) }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val showsTime = settings.corners.any { it == CornerData.TIME }
    LaunchedEffect(showsTime) {
        while (showsTime) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val g = accel.value
    val incline = if (g != null) Math.toDegrees(atan2(g[1].toDouble(), g[2].toDouble())).roundToInt() else null
    val altitude: Double? = location?.takeIf { it.hasAltitude() }?.altitude
        ?: baro.value?.let { SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it[0]).toDouble() }
    val shownHeading = heading.roundToInt() % 360

    fun textFor(d: CornerData): String = when (d) {
        CornerData.EMPTY -> ""
        CornerData.ALTITUDE -> altitude?.let { "Altitude ${it.roundToInt()} m" } ?: "Altitude --"
        CornerData.INCLINE -> incline?.let { "Incline $it°" } ?: "Incline --"
        CornerData.LATITUDE -> location?.let { dms(it.latitude, "N", "S") } ?: "-- N"
        CornerData.LONGITUDE -> location?.let { dms(it.longitude, "E", "W") } ?: "-- E"
        CornerData.SPEED -> location?.takeIf { it.hasSpeed() }?.let { "Speed ${(it.speed * 3.6f).roundToInt()} km/h" } ?: "Speed --"
        CornerData.PRESSURE -> baro.value?.let { "Pressure ${it[0].roundToInt()} hPa" } ?: "Pressure --"
        CornerData.TIME -> android.text.format.DateFormat.getTimeFormat(context).format(Date(now))
    }
    val texts = settings.corners.map { textFor(it) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!editing) IconButton(onClick = { editing = true }) { Icon(painterResource(R.drawable.tool_edit), contentDescription = "Customize") }
        }
        Dial(
            settings = settings, heading = heading, shownHeading = shownHeading, texts = texts,
            editing = editing, selected = selected,
            onSelect = { if (editing) selected = it },
            onHide = { i -> settings.change { corners[i] = CornerData.EMPTY } },
        )
        if (needsLocation && !hasPermission) {
            TextButton(
                onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Allow location for altitude, latitude, longitude and speed") }
        }
        if (v == null) Text("Waiting for the sensor…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing) {
            Customize(settings, selected, onClearSelection = { selected = -1 }, onDone = { editing = false; selected = -1 })
        } else {
            Text(
                "Keep the phone away from magnets and metal for the best reading.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How far the shape's edge comes in towards the centre at its closest, as a fraction of its half width.
 * Ticks are laid out inside this radius, so a shape with deep valleys (the sunny ones) never cuts them off.
 */
private fun innerRadius(shape: Shape): Float {
    val outline = shape.createOutline(Size(1000f, 1000f), LayoutDirection.Ltr, Density(1f))
    val path = (outline as? Outline.Generic)?.path ?: return 1f
    val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
    val pos = FloatArray(2)
    var closest = Float.MAX_VALUE
    do {
        val len = measure.length
        if (len <= 0f) continue
        var d = 0f
        while (d < len) {
            measure.getPosTan(d, pos, null)
            closest = min(closest, hypot(pos[0] - 500f, pos[1] - 500f))
            d += len / 720f
        }
    } while (measure.nextContour())
    return if (closest == Float.MAX_VALUE) 1f else (closest / 500f).coerceIn(0.5f, 1f)
}

/** Degrees from the heading to the nearest of north, east, south and west. */
private fun offCardinal(heading: Float): Float {
    val r = ((heading % 90f) + 90f) % 90f
    return min(r, 90f - r)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Dial(
    settings: CompassSettings,
    heading: Float,
    shownHeading: Int,
    texts: List<String>,
    editing: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    onHide: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val face = scheme.primary
    val onFace = scheme.onPrimary
    val north = scheme.error
    val cornerInk = scheme.onSurfaceVariant
    val pillFill = scheme.surfaceContainerLow
    val pillRing = scheme.outlineVariant
    val pillSelected = scheme.primary
    val shapeIndex = settings.shape.coerceIn(0, DIAL_SHAPES.lastIndex)
    val shape = DIAL_SHAPES[shapeIndex].second.toShape()
    val rMin = remember(shape) { innerRadius(shape) }
    val corners = settings.corners
    val tickInk = onFace

    // the pointer grows, with a bounce, when the phone faces north, east, south or west
    var snapped by remember { mutableStateOf(false) }
    LaunchedEffect(heading) {
        val off = offCardinal(heading)
        if (snapped && off > 3f) snapped = false else if (!snapped && off <= 1.5f) snapped = true
    }
    val pointerScale by animateFloatAsState(
        targetValue = if (snapped) 1.7f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "pointer",
    )

    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val box = maxWidth
        val density = LocalDensity.current
        val dialR = box * DIAL_FRACTION / 2
        val u = dialR * rMin // the radius everything inside the dial is laid out in

        // the four corner texts, and in edit mode the pills round them
        Canvas(
            Modifier.fillMaxSize().pointerInput(editing) {
                if (editing) detectTapGestures { p ->
                    val dx = p.x - size.width / 2f
                    val dy = p.y - size.height / 2f
                    val inside = hypot(dx, dy) < size.width * DIAL_FRACTION / 2f * 0.97f
                    onSelect(if (inside) -1 else if (dy < 0) (if (dx < 0) 0 else 1) else (if (dx < 0) 2 else 3))
                }
            },
        ) {
            val half = size.minDimension / 2f
            val r0 = half * DIAL_FRACTION
            val textSize = r0 * 0.10f
            val cap = textSize * 0.72f
            val topR = r0 * 1.06f // glyphs of the top texts point outwards from here
            val botR = topR + cap // the bottom ones point inwards, so they sit a cap height further out
            val pillR = topR + cap / 2f
            val band = r0 * 0.21f
            val ring = 1.5.dp.toPx()
            val cx = center.x
            val cy = center.y
            val mids = floatArrayOf(225f, 315f, 135f, 45f)
            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = cornerInk.toArgb()
                    this.textSize = textSize
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                }
                val cross = Paint(text).apply { this.textSize = textSize * 1.6f }
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                for (i in 0 until 4) {
                    val top = i < 2
                    val mid = mids[i]
                    if (editing) {
                        val rect = RectF(cx - pillR, cy - pillR, cx + pillR, cy + pillR)
                        val pill = Path().apply { addArc(rect, if (top) mid - 37f else mid + 37f, if (top) 74f else -74f) }
                        stroke.strokeWidth = band + ring * 2
                        stroke.color = (if (i == selected) pillSelected else pillRing).toArgb()
                        n.drawPath(pill, stroke)
                        stroke.strokeWidth = band
                        stroke.color = pillFill.toArgb()
                        n.drawPath(pill, stroke)
                    }
                    val radius = if (top) topR else botR
                    val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                    val path = Path().apply { if (top) addArc(rect, mid - 45f, 90f) else arcTo(rect, mid + 45f, -90f) }
                    val len = radius * (PI.toFloat() / 2f)
                    val label = if (editing && texts[i].isEmpty()) "×" else texts[i]
                    val paint = if (texts[i].isEmpty()) cross else text
                    if (label.isNotEmpty()) {
                        // the cross is centred a little lower so it sits in the middle of the pill
                        val v = if (texts[i].isEmpty()) (if (top) cap * 0.45f else -cap * 0.45f) else 0f
                        n.drawTextOnPath(label, path, ((len - paint.measureText(label)) / 2f).coerceAtLeast(0f), v, paint)
                    }
                }
            }
        }

        // the dial turns against the heading
        Box(
            Modifier
                .align(Alignment.Center)
                .size(dialR * 2)
                .rotate(-heading)
                .clip(shape)
                .background(face),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val k = size.minDimension / 2f * rMin
                val outer = k * 0.925f
                for (i in 0 until 72) {
                    val a = i * 5 * PI / 180
                    val major = i % 18 == 0
                    val mid = i % 6 == 0
                    val len = (if (major) 0.15f else if (mid) 0.105f else 0.06f) * k
                    val w = (if (major) 3.2f else if (mid) 2.4f else 1.2f).dp.toPx()
                    val s = Offset(c.x + outer * sin(a).toFloat(), c.y - outer * cos(a).toFloat())
                    val e = Offset(c.x + (outer - len) * sin(a).toFloat(), c.y - (outer - len) * cos(a).toFloat())
                    drawLine(if (i == 0 && settings.redNorth) north else tickInk, s, e, strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = k * 0.17f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                }
                val small = Paint(big).apply { textSize = k * 0.13f }
                val letters = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                drawIntoCanvas { canvas ->
                    val n = canvas.nativeCanvas
                    for (i in 0 until 8) {
                        val cardinal = i % 2 == 0
                        if (!cardinal && settings.fourPoints) continue
                        val paint = if (cardinal) big else small
                        paint.color = if (i == 0 && settings.redNorth) north.toArgb() else onFace.toArgb()
                        val text = if (settings.numbers) (i * 45).toString() else letters[i]
                        n.save()
                        n.rotate(i * 45f, c.x, c.y)
                        val radius = k * (if (cardinal) 0.6f else 0.57f)
                        n.drawText(text, c.x, c.y - radius + paint.textSize * 0.35f, paint)
                        n.restore()
                    }
                }
            }
        }

        // the pointer that stays put: a small rounded triangle, larger when facing a main direction
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val k = size.minDimension / 2f * DIAL_FRACTION * rMin
            val a = k * 0.022f
            val h = k * 0.018f
            val round = k * 0.014f
            fun pointer(cy: Float, flip: Boolean) {
                withTransform({
                    scale(pointerScale, pointerScale, Offset(c.x, cy))
                    if (flip) rotate(180f, Offset(c.x, cy))
                }) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(c.x, cy - h * 1.15f)
                        lineTo(c.x - a, cy + h * 0.9f)
                        lineTo(c.x + a, cy + h * 0.9f)
                        close()
                    }
                    drawPath(path, onFace)
                    drawPath(path, onFace, style = Stroke(width = round * 2, join = StrokeJoin.Round))
                }
            }
            pointer(c.y - k * 0.46f, false)
            if (settings.doubleArrow) pointer(c.y + k * 0.46f, true)
        }

        // the heading, a little above the middle
        val fontSize = with(density) { (u * 0.31f).toSp() }
        Text(
            "$shownHeading°",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, lineHeight = fontSize),
            fontWeight = FontWeight.ExtraBold,
            color = onFace,
            modifier = Modifier.align(Alignment.Center).offset(y = -(u * 0.1f)),
        )

        // in edit mode a selected corner with data gets a button that hides it
        if (editing && selected in 0..3 && corners[selected] != CornerData.EMPTY) {
            val pillR = dialR * 1.06f + dialR * 0.1f * 0.72f / 2f + dialR * 0.06f
            val angle = Math.toRadians(doubleArrayOf(195.0, 285.0, 165.0, 75.0)[selected])
            val x = box / 2 + pillR * cos(angle).toFloat() - 20.dp
            val y = box / 2 + pillR * sin(angle).toFloat() - 20.dp
            Box(
                Modifier.offset(x, y).size(40.dp).clip(CircleShape).background(scheme.errorContainer).clickable { onHide(selected) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.cc_hide), contentDescription = "Hide", tint = scheme.onErrorContainer, modifier = Modifier.size(22.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Customize(settings: CompassSettings, selectedCorner: Int, onClearSelection: () -> Unit, onDone: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedCorner < 0 && tab == 0, onClick = { tab = 0; onClearSelection() }, label = { Text("Compass Style") },
                leadingIcon = { Icon(painterResource(R.drawable.tool_palette), null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = selectedCorner < 0 && tab == 1, onClick = { tab = 1; onClearSelection() }, label = { Text("Compass Features") },
                leadingIcon = { Icon(painterResource(R.drawable.tool_tune), null, Modifier.size(18.dp)) },
            )
        }
        Inputs {
            if (selectedCorner in 0..3) {
                Text("Corner Data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose what the " + listOf("top left", "top right", "bottom left", "bottom right")[selectedCorner] + " corner shows.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(CornerData.entries) { _, data ->
                        val selected = settings.corners[selectedCorner] == data
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(24.dp)) else Modifier)
                                    .clickable { settings.change { corners[selectedCorner] = data } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(data.iconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                            }
                            Text(data.label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (tab == 0) {
                Text("Compass shape", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(DIAL_SHAPES) { i, (name, polygon) ->
                        val selected = i == settings.shape
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(24.dp)) else Modifier)
                                    .clickable { settings.change { shape = i } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(52.dp).clip(polygon.toShape()).background(MaterialTheme.colorScheme.primary))
                            }
                            Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Tap a corner of the compass to choose what it shows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SwitchRow("Red north line", settings.redNorth) { settings.change { redNorth = it } }
                SwitchRow("Vibration tick every degree", settings.vibrate) { settings.change { vibrate = it } }
                OptionRow("Arrow style", listOf("Single", "Double"), if (settings.doubleArrow) 1 else 0) { settings.change { doubleArrow = it == 1 } }
                OptionRow("Dial format", listOf("A-Z", "0-360"), if (settings.numbers) 1 else 0) { settings.change { numbers = it == 1 } }
                OptionRow("Cardinal points", listOf("8", "4"), if (settings.fourPoints) 1 else 0) { settings.change { fourPoints = it == 1 } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { settings.reset() }, modifier = Modifier.weight(1f)) { Text("Reset") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Choice(options, selected, onSelect = onSelect)
    }
}
