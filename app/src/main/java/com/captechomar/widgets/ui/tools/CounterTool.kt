package com.captechomar.widgets.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A tally: plus on top, the value in the middle, minus below, then reset back to the starting value. */
@Composable
fun CounterTool() {
    val haptic = LocalHapticFeedback.current
    var start by rememberSaveable { mutableStateOf("0") }
    var count by rememberSaveable { mutableLongStateOf(0L) }
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            OutlinedTextField(
                value = start,
                onValueChange = { s ->
                    if (s.length <= 12 && s.withIndex().all { (i, c) -> c.isDigit() || (i == 0 && c == '-') }) {
                        start = s
                        count = s.toLongOrNull() ?: 0L
                    }
                },
                label = { Text("Starting value") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = { count++; tick() },
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.fillMaxWidth().height(110.dp),
        ) { Text("+", style = MaterialTheme.typography.displayMedium) }
        Text(
            count.toString(),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        FilledTonalButton(
            onClick = { count--; tick() },
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.fillMaxWidth().height(110.dp),
        ) { Text("−", style = MaterialTheme.typography.displayMedium) }
        OutlinedButton(
            onClick = { count = start.toLongOrNull() ?: 0L; tick() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Reset", style = MaterialTheme.typography.titleMedium) }
    }
}
