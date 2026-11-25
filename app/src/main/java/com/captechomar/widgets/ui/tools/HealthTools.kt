package com.captechomar.widgets.ui.tools

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val KG_PER_LB = 0.45359237
private const val CM_PER_IN = 2.54

/** Height and weight fields in the unit system picked above them. Values come back in cm and kg. */
@Composable
private fun BodyInputs(
    withHeight: Boolean = true,
    withWeight: Boolean = true,
    result: @Composable (heightCm: Double?, weightKg: Double?, metric: Boolean) -> Unit,
) {
    var unit by rememberSaveable { mutableIntStateOf(0) }
    var cm by rememberSaveable { mutableStateOf("") }
    var kg by rememberSaveable { mutableStateOf("") }
    var ft by rememberSaveable { mutableStateOf("") }
    var inch by rememberSaveable { mutableStateOf("") }
    var lb by rememberSaveable { mutableStateOf("") }
    val metric = unit == 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            Choice(listOf("Metric", "Imperial"), unit) { unit = it }
            if (withHeight) {
                if (metric) NumField("Height", cm, { cm = it }, suffix = "cm")
                else Row2({ NumField("Feet", ft, { v -> ft = v }, it, "ft") }, { NumField("Inches", inch, { v -> inch = v }, it, "in") })
            }
            if (withWeight) {
                if (metric) NumField("Weight", kg, { kg = it }, suffix = "kg") else NumField("Weight", lb, { lb = it }, suffix = "lb")
            }
        }
        val h = if (metric) cm.toNum() else {
            val f = ft.toNum() ?: 0.0
            val i = inch.toNum() ?: 0.0
            if (ft.isBlank() && inch.isBlank()) null else (f * 12 + i) * CM_PER_IN
        }
        val w = if (metric) kg.toNum() else lb.toNum()?.times(KG_PER_LB)
        result(h?.takeIf { it > 0 }, w?.takeIf { it > 0 }, metric)
    }
}

private fun weightText(kg: Double, metric: Boolean) = if (metric) "${fmt(kg)} kg" else "${fmt(kg / KG_PER_LB)} lb"

@Composable
fun BmiTool() {
    var male by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs { Choice(listOf("Male", "Female"), if (male) 0 else 1) { male = it == 0 } }
        BodyInputs { h, w, metric ->
            val dim = MaterialTheme.colorScheme.onSurfaceVariant
            if (h != null && w != null) {
                val m = h / 100
                val bmi = w / (m * m)
                val label = when {
                    bmi < 18.5 -> "Underweight"
                    bmi < 25 -> "Healthy weight"
                    bmi < 30 -> "Overweight"
                    else -> "Obesity"
                }
                ResultCard("Your BMI", fmt(bmi), label)
            }
            if (h != null) {
                val inchesOver5ft = (h / CM_PER_IN - 60).coerceAtLeast(0.0)
                // two common formulas: Devine and Robinson
                val devine = (if (male) 50.0 else 45.5) + 2.3 * inchesOver5ft
                val robinson = (if (male) 52.0 else 49.0) + (if (male) 1.9 else 1.7) * inchesOver5ft
                val avg = (devine + robinson) / 2
                val m = h / 100
                val lo = 18.5 * m * m
                val hi = 24.9 * m * m
                val diff = if (w != null) w - avg else null
                val note = when {
                    diff == null -> "Healthy BMI range: ${weightText(lo, metric)} to ${weightText(hi, metric)}"
                    kotlin.math.abs(diff) < 0.5 -> "You are right at your ideal weight"
                    diff > 0 -> "${weightText(diff, metric)} above it. Healthy range: ${weightText(lo, metric)} to ${weightText(hi, metric)}"
                    else -> "${weightText(-diff, metric)} below it. Healthy range: ${weightText(lo, metric)} to ${weightText(hi, metric)}"
                }
                ResultCard("Ideal weight", weightText(avg, metric), note)
                Text(
                    "Ideal weight is the average of the Devine (${weightText(devine, metric)}) and Robinson (${weightText(robinson, metric)}) formulas. " +
                        "BMI and these formulas are rough guides. They do not tell muscle from fat, so use them as a starting point and not as a diagnosis.",
                    style = MaterialTheme.typography.bodyMedium, color = dim,
                )
            } else Text("Enter your height, and your weight for the BMI.", style = MaterialTheme.typography.bodyLarge, color = dim)
        }
    }
}

@Composable
fun WaterTool() {
    var exercise by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BodyInputs(withHeight = false) { _, w, metric ->
            Inputs { NumField("Exercise per day", exercise, { exercise = it }, suffix = "min") }
            if (w != null) {
                val ml = w * 33 + (exercise.toNum() ?: 0.0) / 30 * 350
                val litres = ml / 1000
                val text = if (metric) "${fmt(litres, 1)} L" else "${fmt(ml / 29.5735, 0)} fl oz"
                ResultCard("Daily water", text, "About ${(ml / 250).toInt()} glasses of 250 ml")
                Text(
                    "A general estimate: 33 ml per kg of body weight plus 350 ml for every 30 minutes of exercise. Heat, illness and pregnancy change it, and food and other drinks count too.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else Text("Enter your weight.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * When to fall asleep or wake up. A night is made of sleep cycles of about 90 minutes and waking at the end of one is
 * easier than waking in the middle, so this lists the times that line up with 3 to 6 cycles, allowing 15 minutes to drop off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTool() {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val picker = rememberTimePickerState(initialHour = 7, initialMinute = 0, is24Hour = DateFormat.is24HourFormat(context))
    val format = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val now = remember(mode) { LocalTime.now() }
    val cycleMinutes = 90L
    val fallAsleepMinutes = 15L
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs { Choice(listOf("Wake up at", "Go to bed at", "Sleep now"), mode) { mode = it } }
        if (mode != 2) {
            Text(
                if (mode == 0) "I want to wake up at" else "I plan to go to bed at",
                style = MaterialTheme.typography.titleMedium,
            )
            TimeInput(picker)
        }
        val base = if (mode == 2) now else LocalTime.of(picker.hour, picker.minute)
        // wake-up mode lists the earliest bedtime first (6 cycles); the others list the shortest night first
        val cycles = if (mode == 0) listOf(6, 5, 4, 3) else listOf(3, 4, 5, 6)
        Text(
            when (mode) {
                0 -> "Fall asleep at one of these times:"
                1 -> "Wake up at one of these times:"
                else -> "If you fall asleep now, wake up at one of these times:"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        for (c in cycles) {
            val minutes = c * cycleMinutes
            val time = if (mode == 0) base.minusMinutes(minutes + fallAsleepMinutes) else base.plusMinutes(minutes + fallAsleepMinutes)
            val hours = minutes / 60.0
            val sleep = if (hours % 1.0 == 0.0) "${hours.toInt()} h" else "${fmt(hours)} h"
            val tip = if (c >= 5) " · Recommended" else ""
            ResultCard(if (mode == 0) "Go to bed at" else "Wake up at", time.format(format), "$c cycles · $sleep of sleep$tip")
        }
        Text(
            "A sleep cycle lasts about 90 minutes and 15 minutes are allowed for falling asleep. Most adults feel best after 5 or 6 cycles, which is 7.5 to 9 hours. Everyone is a little different, so treat the times as a guide.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
