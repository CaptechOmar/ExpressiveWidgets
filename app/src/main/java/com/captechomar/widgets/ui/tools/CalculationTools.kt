package com.captechomar.widgets.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val hint @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val longDateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"}"

/** A date as "3 years, 2 months and 5 days"; empty parts are left out. */
private fun spanText(p: Period): String {
    val parts = buildList {
        if (p.years != 0) add(plural(p.years.toLong(), "year"))
        if (p.months != 0) add(plural(p.months.toLong(), "month"))
        if (p.days != 0 || isEmpty()) add(plural(p.days.toLong(), "day"))
    }
    return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

/** A button that shows a date and opens the calendar picker. The date is kept as an epoch day so it survives rotation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, epochDay: Long?, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (epochDay == null) "$label: pick a date" else "$label: ${LocalDate.ofEpochDay(epochDay).format(dateFormat)}")
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay?.let { it * 86_400_000L })
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun AgeTool() {
    var birth by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs { DateField("Date of birth", birth) { birth = it } }
        val today = LocalDate.now()
        val b = birth?.let { LocalDate.ofEpochDay(it) }
        when {
            b == null -> Text("Pick the date of birth.", style = MaterialTheme.typography.bodyLarge, color = hint)
            b.isAfter(today) -> Text("That date is in the future.", style = MaterialTheme.typography.bodyLarge, color = hint)
            else -> {
                val age = Period.between(b, today)
                ResultCard("Your age", plural(age.years.toLong(), "year"), "and ${plural(age.months.toLong(), "month")} and ${plural(age.days.toLong(), "day")}")
                var next = b.withYear(today.year)
                if (!next.isAfter(today)) next = b.withYear(today.year + 1)
                val toGo = ChronoUnit.DAYS.between(today, next)
                ResultCard("Next birthday", if (toGo == 0L) "Today" else plural(toGo, "day"), next.format(longDateFormat))
                ResultCard("Days lived", "%,d".format(ChronoUnit.DAYS.between(b, today)), "About ${"%,d".format(ChronoUnit.WEEKS.between(b, today))} weeks")
            }
        }
    }
}

@Composable
fun DaysUntilTool() {
    var target by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs { DateField("Date", target) { target = it } }
        val t = target?.let { LocalDate.ofEpochDay(it) }
        if (t == null) Text("Pick a date.", style = MaterialTheme.typography.bodyLarge, color = hint)
        else {
            val days = ChronoUnit.DAYS.between(LocalDate.now(), t)
            val away = abs(days)
            val value = when {
                days == 0L -> "Today"
                days > 0 -> plural(days, "day")
                else -> "${plural(away, "day")} ago"
            }
            val note = if (away >= 7) "${plural(away / 7, "week")} and ${plural(away % 7, "day")} · ${t.format(longDateFormat)}" else t.format(longDateFormat)
            ResultCard(if (days >= 0) "Days until" else "Days since", value, note)
        }
    }
}

@Composable
fun DateDifferenceTool() {
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            DateField("From", from) { from = it }
            DateField("To", to) { to = it }
        }
        val a = from?.let { LocalDate.ofEpochDay(it) }
        val b = to?.let { LocalDate.ofEpochDay(it) }
        if (a == null || b == null) Text("Pick both dates.", style = MaterialTheme.typography.bodyLarge, color = hint)
        else {
            val (early, late) = if (a.isAfter(b)) b to a else a to b
            val days = ChronoUnit.DAYS.between(early, late)
            ResultCard("Difference", spanText(Period.between(early, late)), "${plural(days, "day")} in total")
            ResultCard("In weeks", "${"%,d".format(days / 7)}", "and ${plural(days % 7, "day")}")
        }
    }
}

private class Country(val code: String, val name: String)

private fun offsetText(totalSeconds: Int): String {
    val sign = if (totalSeconds < 0) "-" else "+"
    val abs = abs(totalSeconds)
    val h = abs / 3600
    val m = abs % 3600 / 60
    return "UTC$sign$h" + if (m != 0) ":%02d".format(m) else ""
}

/** Pick a country and see which time zones it uses and what time it is there now. */
@Composable
fun TimeZoneTool() {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val countries = remember {
        Locale.getISOCountries()
            .map { Country(it, Locale("", it).getDisplayCountry(Locale.getDefault())) }
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; selected = null },
                label = { Text("Country") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selected == null && query.isNotBlank()) {
                val matches = countries.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(6)
                if (matches.isEmpty()) Text("No country found.", style = MaterialTheme.typography.bodyMedium, color = hint)
                matches.forEach { c ->
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clickable { selected = c.code; query = c.name }.padding(vertical = 12.dp),
                    )
                }
            }
        }
        val code = selected
        if (code == null) Text("Type a country and pick it.", style = MaterialTheme.typography.bodyLarge, color = hint)
        else {
            val now = remember(code) { Instant.now() }
            val zones = remember(code) {
                android.icu.util.TimeZone.getAvailableIDs(code)
                    .mapNotNull { id -> runCatching { ZoneId.of(id) }.getOrNull() }
                    .distinctBy { it.id }
            }
            if (zones.isEmpty()) Text("No time zone is listed for that country.", style = MaterialTheme.typography.bodyLarge, color = hint)
            else {
                val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                // zones that share the same offset right now are shown together
                zones.groupBy { it.rules.getOffset(now).totalSeconds }.toSortedMap().forEach { (seconds, group) ->
                    val cities = group.map { it.id.substringAfterLast('/').replace('_', ' ') }.sorted()
                    val title = if (cities.size > 3) cities.take(3).joinToString(", ") + " and ${cities.size - 3} more" else cities.joinToString(", ")
                    val local = ZonedDateTime.ofInstant(now, group.first())
                    ResultCard(title, offsetText(seconds), "It is ${local.format(timeFormat)} there now · ${local.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))}")
                }
                Text(
                    "Zones are shown with their current offset from UTC, daylight saving included.",
                    style = MaterialTheme.typography.bodyMedium, color = hint,
                )
            }
        }
    }
}
