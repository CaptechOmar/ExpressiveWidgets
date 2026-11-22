package com.captechomar.widgets.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.ui.Container
import kotlin.math.pow
import kotlin.math.roundToInt

private val hint @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun TipTool() {
    var bill by rememberSaveable { mutableStateOf("") }
    var tip by rememberSaveable { mutableFloatStateOf(15f) }
    var people by rememberSaveable { mutableFloatStateOf(1f) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            NumField("Bill", bill, { bill = it })
            Text("Tip: ${tip.roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(tip, { tip = it }, valueRange = 0f..40f)
            Text("People: ${people.roundToInt()}", style = MaterialTheme.typography.titleMedium)
            Slider(people, { people = it }, valueRange = 1f..20f)
        }
        val b = bill.toNum()
        if (b != null) {
            val tipAmount = b * tip.roundToInt() / 100
            val total = b + tipAmount
            val n = people.roundToInt()
            ResultCard("Each pays", fmt(total / n, 2), "Total ${fmt(total, 2)}, tip ${fmt(tipAmount, 2)}")
        } else Text("Enter the bill.", style = MaterialTheme.typography.bodyLarge, color = hint)
    }
}

@Composable
fun LoanTool() {
    var amount by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var years by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            NumField("Loan amount", amount, { amount = it })
            Row2({ NumField("Interest", rate, { v -> rate = v }, it, "% / yr") }, { NumField("Term", years, { v -> years = v }, it, "years") })
        }
        val p = amount.toNum()
        val y = years.toNum()
        val r = rate.toNum() ?: 0.0
        if (p != null && y != null && y > 0) {
            val n = (y * 12).roundToInt().coerceAtLeast(1)
            val i = r / 100 / 12
            val monthly = if (i == 0.0) p / n else p * i / (1 - (1 + i).pow(-n))
            val total = monthly * n
            ResultCard("Monthly payment", fmt(monthly, 2), "Total ${fmt(total, 2)}, interest ${fmt(total - p, 2)}")
        } else Text("Enter the amount and the term.", style = MaterialTheme.typography.bodyLarge, color = hint)
    }
}

@Composable
fun DiscountTool() {
    var price by rememberSaveable { mutableStateOf("") }
    var off by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            Row2({ NumField("Price", price, { v -> price = v }, it) }, { NumField("Discount", off, { v -> off = v }, it, "%") })
        }
        val p = price.toNum()
        val d = off.toNum()
        if (p != null && d != null) {
            val saved = p * d / 100
            ResultCard("You pay", fmt(p - saved, 2), "You save ${fmt(saved, 2)}")
        } else Text("Enter the price and the discount.", style = MaterialTheme.typography.bodyLarge, color = hint)
    }
}
