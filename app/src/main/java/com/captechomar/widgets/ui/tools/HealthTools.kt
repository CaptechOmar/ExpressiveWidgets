package com.captechomar.widgets.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

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
    BodyInputs { h, w, _ ->
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
            Text(
                "BMI is a rough guide. It does not tell muscle from fat, so use it as a starting point and not as a diagnosis.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else Text("Enter your height and weight.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun IdealWeightTool() {
    var male by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs { Choice(listOf("Male", "Female"), if (male) 0 else 1) { male = it == 0 } }
        BodyInputs(withWeight = false) { h, _, metric ->
            if (h != null) {
                val inchesOver5ft = (h / CM_PER_IN - 60).coerceAtLeast(0.0)
                // two common formulas: Devine and Robinson
                val devine = (if (male) 50.0 else 45.5) + 2.3 * inchesOver5ft
                val robinson = (if (male) 52.0 else 49.0) + (if (male) 1.9 else 1.7) * inchesOver5ft
                val avg = (devine + robinson) / 2
                val m = h / 100
                val lo = 18.5 * m * m
                val hi = 24.9 * m * m
                ResultCard("Ideal weight", weightText(avg, metric), "Healthy BMI range: ${weightText(lo, metric)} to ${weightText(hi, metric)}")
                Text(
                    "Average of the Devine (${weightText(devine, metric)}) and Robinson (${weightText(robinson, metric)}) formulas. They are estimates, not targets.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else Text("Enter your height.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
