package com.captechomar.widgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.Calc
import com.captechomar.widgets.data.SavingsStore
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.SavingsKind
import com.captechomar.widgets.widgets.SavingsRenderer

/** Quick calculator: type an amount (or a sum like 120+35) and add it, take it away, or set the saved amount to it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdjustDialog(currency: String, saved: Double, onApply: (newSaved: Double) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val value = Calc.eval(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust savings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saved now: ${SavingsRenderer.format(currency, saved)}", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Amount, e.g. 120+35") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (value != null && input.any { !it.isDigit() && it != '.' }) Text("= ${SavingsRenderer.format("", value)}") },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = value != null, onClick = { onApply(saved + value!!) }) { Text("Add") }
                    FilledTonalButton(enabled = value != null, onClick = { onApply(saved - value!!) }) { Text("Subtract") }
                    OutlinedButton(enabled = value != null, onClick = { onApply(value!!) }) { Text("Set to") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Opened from the emoji tile of a savings widget. */
class SavingsAdjustActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val cfg = WidgetPrefs.load(this, id, SavingsKind.defaultConfig)
        setContent {
            AppTheme {
                AdjustDialog(
                    currency = cfg.savings.currency,
                    saved = cfg.savings.saved,
                    onApply = { v ->
                        SavingsStore.mutate(this, id) { it.withSaved(v) }
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}
