package com.captechomar.widgets.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
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

private fun money(v: Double) = "%,.2f".format(v)

/** Annual or monthly pay as an hourly rate, plus the pay for a day, week and month. */
@Composable
fun SalaryTool() {
    var period by rememberSaveable { mutableIntStateOf(0) }
    var pay by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("40") }
    var weeks by rememberSaveable { mutableStateOf("52") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            Choice(listOf("Annual", "Monthly"), period) { period = it }
            NumField(if (period == 0) "Annual salary" else "Monthly salary", pay, { pay = it })
            Row2({ NumField("Hours a week", hours, { v -> hours = v }, it, "h") }, { NumField("Weeks a year", weeks, { v -> weeks = v }, it) })
        }
        val p = pay.toNum()
        val h = hours.toNum()
        val w = weeks.toNum()
        if (p != null && h != null && w != null && h > 0 && w > 0) {
            val annual = if (period == 0) p else p * 12
            val perWeek = annual / w
            val hourly = perWeek / h
            ResultCard("Hourly pay", money(hourly), "Daily ${money(hourly * h / 5)} for a 5-day week")
            ResultCard("Pay per period", money(perWeek), "Weekly. Monthly ${money(annual / 12)}, annual ${money(annual)}")
        } else Text("Enter your pay and your hours.", style = MaterialTheme.typography.bodyLarge, color = hint)
    }
}

/** How savings grow: a starting amount plus a monthly contribution, compounding monthly at the expected return. */
@Composable
fun InvestmentTool() {
    var initial by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var monthly by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf<Double?>(null) }
    var put by rememberSaveable { mutableStateOf<Double?>(null) }
    val m = months.toNum()?.roundToInt()
    val ready = m != null && m > 0 && (initial.toNum() != null || monthly.toNum() != null)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            NumField("Initial investment", initial, { initial = it })
            Row2({ NumField("Expected return", rate, { v -> rate = v }, it, "% / yr") }, { NumField("Time period", months, { v -> months = v }, it, "months") })
            NumField("Monthly contribution", monthly, { monthly = it })
        }
        Button(
            onClick = {
                val start = initial.toNum() ?: 0.0
                val add = monthly.toNum() ?: 0.0
                val i = (rate.toNum() ?: 0.0) / 100 / 12
                var balance = start
                repeat(m ?: 0) { balance = balance * (1 + i) + add }
                total = balance
                put = start + add * (m ?: 0)
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Calculate growth") }
        val t = total
        val c = put
        if (t != null && c != null) {
            ResultCard("Total wealth created", money(t), "From ${money(c)} put in")
            ResultCard("Profit", money(t - c), if (c > 0) "A return of ${fmt((t - c) / c * 100)}% on what you put in" else null)
        } else Text("Fill in the amounts and tap Calculate growth.", style = MaterialTheme.typography.bodyLarge, color = hint)
    }
}
