package com.captechomar.widgets.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.captechomar.widgets.ui.Container

/** A number field: keeps whatever the user typed, [toNum] reads it. */
@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), suffix: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.all { it.isDigit() || it == '.' || it == ',' }) onChange(s) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    )
}

fun String.toNum(): Double? = replace(',', '.').toDoubleOrNull()

/** Two or more choices in a row, like metric / imperial. */
@Composable
fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, text ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(text) }
        }
    }
}

/** The answer of a calculator, big and coloured. */
@Composable
fun ResultCard(title: String, value: String, note: String? = null) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Fields in a rounded container, the way the settings screens are laid out. */
@Composable
fun Inputs(content: @Composable () -> Unit) {
    Container { content() }
}

@Composable
fun Row2(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        a(Modifier.weight(1f))
        b(Modifier.weight(1f))
    }
}

fun fmt(v: Double, digits: Int = 1): String = "%.${digits}f".format(v)
