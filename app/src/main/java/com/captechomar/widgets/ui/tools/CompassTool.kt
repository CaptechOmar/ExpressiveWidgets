package com.captechomar.widgets.ui.tools

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.core.content.ContextCompat
import com.captechomar.widgets.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
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

/** How the compass looks and behaves, saved between visits. */
class CompassSettings(private val prefs: SharedPreferences) {
    var shape by mutableIntStateOf(prefs.getInt("compass_shape", 0))
    var redNorth by mutableStateOf(prefs.getBoolean("compass_red_north", true))
    var doubleArrow by mutableStateOf(prefs.getBoolean("compass_double_arrow", false))
    var numbers by mutableStateOf(prefs.getBoolean("compass_numbers", false))
    var fourPoints by mutableStateOf(prefs.getBoolean("compass_four_points", false))
    var vibrate by mutableStateOf(prefs.getBoolean("compass_vibrate", true))

    fun change(block: CompassSettings.() -> Unit) {
        block()
        prefs.edit()
            .putInt("compass_shape", shape).putBoolean("compass_red_north", redNorth).putBoolean("compass_double_arrow", doubleArrow)
            .putBoolean("compass_numbers", numbers).putBoolean("compass_four_points", fourPoints).putBoolean("compass_vibrate", vibrate)
            .apply()
    }

    fun reset() = change {
        shape = 0
        redNorth = true
        doubleArrow = false
        numbers = false
        fourPoints = false
        vibrate = true
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

@Composable
fun CompassTool() {
    val context = LocalContext.current
    val (available, rotation) = rememberSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (!available) return Missing("compass")
    val (_, accel) = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    val (_, baro) = rememberSensor(Sensor.TYPE_PRESSURE)
    val settings = remember { CompassSettings(context.applicationContext.getSharedPreferences("tools", Context.MODE_PRIVATE)) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var heading by remember { mutableFloatStateOf(0f) }
    var location by remember { mutableStateOf(lastLocation(context)) }
    val vibrator = remember { vibratorOf(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { location = lastLocation(context) }

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

    val g = accel.value
    val incline = if (g != null) Math.toDegrees(atan2(g[1].toDouble(), g[2].toDouble())).roundToInt() else null
    val altitude: Double? = location?.takeIf { it.hasAltitude() }?.altitude
        ?: baro.value?.let { SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it[0]).toDouble() }
    val shownHeading = heading.roundToInt() % 360
    val corners = Corners(
        altitude = altitude?.let { "Altitude ${it.roundToInt()} m" } ?: "Altitude --",
        incline = incline?.let { "Incline $it°" } ?: "Incline --",
        latitude = location?.let { dms(it.latitude, "N", "S") } ?: "-- N",
        longitude = location?.let { dms(it.longitude, "E", "W") } ?: "-- E",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!editing) IconButton(onClick = { editing = true }) { Icon(painterResource(R.drawable.tool_edit), contentDescription = "Customize") }
        }
        Dial(settings, heading, shownHeading, corners)
        if (location == null) {
            TextButton(
                onClick = { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Allow location for latitude, longitude and altitude") }
        }
        if (v == null) Text("Waiting for the sensor…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing) {
            Customize(settings, onDone = { editing = false })
        } else {
            Text(
                "Keep the phone away from magnets and metal for the best reading.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class Corners(val altitude: String, val incline: String, val latitude: String, val longitude: String)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Dial(settings: CompassSettings, heading: Float, shownHeading: Int, corners: Corners) {
    val scheme = MaterialTheme.colorScheme
    val face = scheme.primary
    val onFace = scheme.onPrimary
    val north = scheme.error
    val cornerInk = scheme.onSurfaceVariant
    val shape = DIAL_SHAPES[settings.shape.coerceIn(0, DIAL_SHAPES.lastIndex)].second.toShape()
    val dialFraction = 0.74f

    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        // altitude, incline, latitude and longitude curve round the corners of the dial
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension / 2f
            val dialR = s * dialFraction
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cornerInk.toArgb()
                textSize = s * 0.085f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            fun curved(text: String, start: Float, sweep: Float, radius: Float) {
                val rect = RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
                val path = Path().apply { addArc(rect, start, sweep) }
                val len = radius * (PI.toFloat() / 2f)
                drawIntoCanvas { it.nativeCanvas.drawTextOnPath(text, path, ((len - paint.measureText(text)) / 2f).coerceAtLeast(0f), 0f, paint) }
            }
            // clockwise arcs have their glyphs pointing outwards, counter-clockwise ones inwards
            curved(corners.altitude, 180f, 90f, dialR * 1.1f)
            curved(corners.incline, 270f, 90f, dialR * 1.1f)
        }
        // the two lower texts run the other way round so they stay readable
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension / 2f
            val dialR = s * dialFraction
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cornerInk.toArgb()
                textSize = s * 0.085f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            val radius = dialR * 1.27f
            val rect = RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
            val len = radius * (PI.toFloat() / 2f)
            drawIntoCanvas { c ->
                val left = Path().apply { arcTo(rect, 180f, -90f) } // bottom-left, running from the left round to the bottom
                val right = Path().apply { arcTo(rect, 90f, -90f) } // bottom-right
                c.nativeCanvas.drawTextOnPath(corners.latitude, left, ((len - paint.measureText(corners.latitude)) / 2f).coerceAtLeast(0f), 0f, paint)
                c.nativeCanvas.drawTextOnPath(corners.longitude, right, ((len - paint.measureText(corners.longitude)) / 2f).coerceAtLeast(0f), 0f, paint)
            }
        }

        // the dial turns against the heading
        Box(
            Modifier
                .fillMaxSize(dialFraction)
                .rotate(-heading)
                .clip(shape)
                .background(face),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2f
                for (i in 0 until 72) {
                    val a = i * 5 * PI / 180
                    val major = i % 6 == 0
                    val len = if (major) r * 0.1f else r * 0.05f
                    val outer = r * 0.82f
                    val s = Offset(c.x + outer * sin(a).toFloat(), c.y - outer * cos(a).toFloat())
                    val e = Offset(c.x + (outer - len) * sin(a).toFloat(), c.y - (outer - len) * cos(a).toFloat())
                    val isNorth = i == 0 && settings.redNorth
                    drawLine(if (isNorth) north else onFace, s, e, strokeWidth = (if (major) 2.5f else 1.2f).dp.toPx())
                }
                val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = r * 0.17f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                }
                val small = Paint(big).apply { textSize = r * 0.13f }
                val letters = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                drawIntoCanvas { canvas ->
                    val n = canvas.nativeCanvas
                    for (k in 0 until 8) {
                        val cardinal = k % 2 == 0
                        if (!cardinal && settings.fourPoints) continue
                        val paint = if (cardinal) big else small
                        paint.color = if (k == 0 && settings.redNorth) north.toArgb() else onFace.toArgb()
                        val text = if (settings.numbers) (k * 45).toString() else letters[k]
                        n.save()
                        n.rotate(k * 45f, c.x, c.y)
                        // labels sit just inside the ticks; cardinals a little further out than the diagonals
                        val radius = r * (if (cardinal) 0.6f else 0.56f)
                        n.drawText(text, c.x, c.y - radius + paint.textSize * 0.35f, paint)
                        n.restore()
                    }
                }
            }
        }
        // the arrow that stays put: the way you are facing
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val s = size.minDimension / 2f
            val h = s * 0.075f
            fun arrow(tipY: Float, dir: Float) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, tipY)
                    lineTo(c.x - h * 0.85f, tipY + dir * h * 1.6f)
                    lineTo(c.x + h * 0.85f, tipY + dir * h * 1.6f)
                    close()
                }
                drawPath(path, onFace)
            }
            val top = c.y - s * dialFraction * 0.5f
            arrow(top, 1f)
            if (settings.doubleArrow) arrow(c.y + s * dialFraction * 0.5f, -1f)
        }
        Text(
            "$shownHeading°",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.ExtraBold,
            color = onFace,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Customize(settings: CompassSettings, onDone: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == 0, onClick = { tab = 0 }, label = { Text("Compass Style") },
                leadingIcon = { Icon(painterResource(R.drawable.tool_palette), null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = tab == 1, onClick = { tab = 1 }, label = { Text("Compass Features") },
                leadingIcon = { Icon(painterResource(R.drawable.tool_tune), null, Modifier.size(18.dp)) },
            )
        }
        Inputs {
            if (tab == 0) {
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
