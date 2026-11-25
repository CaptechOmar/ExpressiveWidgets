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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.captechomar.widgets.R
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
internal fun rememberSensor(type: Int): Pair<Boolean, State<FloatArray?>> {
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
internal fun Missing(what: String, iconRes: Int) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().heightIn(min = 460.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(painterResource(iconRes), null, Modifier.size(72.dp), tint = hint)
        Text("This phone has no $what sensor.", style = MaterialTheme.typography.bodyLarge, color = hint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ---------------------------------------------------------------- barometer

@Composable
fun BarometerTool() {
    val (available, pressure) = rememberSensor(Sensor.TYPE_PRESSURE)
    if (!available) return Missing("air pressure", R.drawable.tool_barometer)
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
