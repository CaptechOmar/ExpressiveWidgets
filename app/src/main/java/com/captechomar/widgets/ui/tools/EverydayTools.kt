package com.captechomar.widgets.ui.tools

import android.content.Context
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.roundToInt

private val hint @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Live values of one sensor, or null until the first reading. [available] says if the phone has the sensor at all. */
@Composable
private fun rememberSensor(type: Int): Pair<Boolean, State<FloatArray?>> {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { manager.getDefaultSensor(type) }
    val values = remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                values.value = e.values.clone()
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return (sensor != null) to values
}

@Composable
private fun Missing(what: String) {
    Text("This phone has no $what sensor.", style = MaterialTheme.typography.bodyLarge, color = hint)
}

// ---------------------------------------------------------------- compass

@Composable
fun CompassTool() {
    val (available, rotation) = rememberSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (!available) return Missing("compass")
    var heading by remember { mutableStateOf(0f) }
    val v = rotation.value
    LaunchedEffect(v) {
        if (v == null) return@LaunchedEffect
        val m = FloatArray(9)
        val o = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(m, v)
        SensorManager.getOrientation(m, o)
        val target = ((Math.toDegrees(o[0].toDouble()).toFloat() + 360f) % 360f)
        // move along the shortest way round so the dial does not spin when crossing north
        var d = target - heading
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        heading = (heading + d * 0.25f + 360f) % 360f
    }
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val dir = names[((heading + 22.5f) / 45f).toInt() % 8]
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ResultCard("Heading", "${heading.roundToInt()}° $dir")
        val ring = MaterialTheme.colorScheme.outline
        val north = MaterialTheme.colorScheme.error
        val face = MaterialTheme.colorScheme.tertiaryContainer
        val onFace = MaterialTheme.colorScheme.onTertiaryContainer
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().rotate(-heading)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(face)
                    drawCircle(ring, style = Stroke(2.dp.toPx()))
                    val c = center
                    val r = size.minDimension / 2
                    for (i in 0 until 72) {
                        val a = i * 5 * PI / 180
                        val len = if (i % 6 == 0) 18.dp.toPx() else 9.dp.toPx()
                        val s = Offset(c.x + (r - 4.dp.toPx()) * kotlin.math.sin(a).toFloat(), c.y - (r - 4.dp.toPx()) * kotlin.math.cos(a).toFloat())
                        val e = Offset(c.x + (r - 4.dp.toPx() - len) * kotlin.math.sin(a).toFloat(), c.y - (r - 4.dp.toPx() - len) * kotlin.math.cos(a).toFloat())
                        drawLine(if (i == 0) north else onFace, s, e, strokeWidth = if (i % 6 == 0) 2.5.dp.toPx() else 1.dp.toPx())
                    }
                }
                Text("N", color = north, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
                Text("S", color = onFace, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
                Text("W", color = onFace, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterStart).padding(start = 30.dp))
                Text("E", color = onFace, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp))
            }
            // the fixed pointer: the way you are facing
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val h = 26.dp.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, 0f)
                    lineTo(c.x - h / 2, h)
                    lineTo(c.x + h / 2, h)
                    close()
                }
                drawPath(path, onFace)
            }
        }
        if (v == null) Text("Waiting for the sensor…", style = MaterialTheme.typography.bodyMedium, color = hint)
        else Text("Keep the phone flat and away from magnets for the best reading.", style = MaterialTheme.typography.bodySmall, color = hint)
    }
}

// ---------------------------------------------------------------- level

@Composable
fun LevelTool() {
    val (available, accel) = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    if (!available) return Missing("motion")
    var smooth by remember { mutableStateOf(floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH)) }
    val raw = accel.value
    LaunchedEffect(raw) { if (raw != null) smooth = FloatArray(3) { smooth[it] + (raw[it] - smooth[it]) * 0.2f } }
    val g = SensorManager.GRAVITY_EARTH
    // tilt of the phone's two edges, in degrees
    val tiltX = Math.toDegrees(asin((smooth[0] / g).coerceIn(-1f, 1f)).toDouble()).toFloat()
    val tiltY = Math.toDegrees(asin((smooth[1] / g).coerceIn(-1f, 1f)).toDouble()).toFloat()
    val flat = abs(tiltX) < 1f && abs(tiltY) < 1f
    val face = if (flat) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val onFace = if (flat) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ResultCard("Tilt", "%.1f° / %.1f°".format(abs(tiltX), abs(tiltY)), if (flat) "Level" else "Tilted")
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val c = center
            val r = size.minDimension / 2
            drawCircle(face)
            drawCircle(onFace, radius = r * 0.12f, center = c, style = Stroke(2.dp.toPx()))
            drawLine(onFace.copy(alpha = 0.4f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(onFace.copy(alpha = 0.4f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            // the bubble drifts to the high side, like a real one
            val bx = (-smooth[0] / g).coerceIn(-1f, 1f) * r * 0.75f
            val by = (smooth[1] / g).coerceIn(-1f, 1f) * r * 0.75f
            drawCircle(onFace, radius = r * 0.1f, center = Offset(c.x + bx, c.y + by))
        }
        Text("Lay the phone on the surface. It reads 0° when flat.", style = MaterialTheme.typography.bodySmall, color = hint)
    }
}

// ---------------------------------------------------------------- barometer

@Composable
fun BarometerTool() {
    val (available, pressure) = rememberSensor(Sensor.TYPE_PRESSURE)
    if (!available) return Missing("air pressure")
    val hpa = pressure.value?.get(0)
    if (hpa == null) {
        Text("Waiting for the sensor…", style = MaterialTheme.typography.bodyLarge, color = hint)
        return
    }
    val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hpa)
    val trend = when {
        hpa < 1000 -> "Low pressure, often unsettled weather"
        hpa > 1020 -> "High pressure, often fair weather"
        else -> "Normal pressure"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResultCard("Air pressure", "%.1f hPa".format(hpa), trend)
        Inputs {
            Text("%.2f inHg".format(hpa * 0.02953), style = MaterialTheme.typography.titleMedium)
            Text("%.1f mmHg".format(hpa * 0.750062), style = MaterialTheme.typography.titleMedium)
            Text("About %.0f m above sea level, if the weather is average".format(altitude), style = MaterialTheme.typography.titleMedium)
        }
    }
}
