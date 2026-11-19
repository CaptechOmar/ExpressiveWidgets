package com.captechomar.widgets.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.SavingsConfig
import com.captechomar.widgets.data.SavingsStore
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.Palettes
import com.captechomar.widgets.widgets.WidgetKind
import kotlin.math.roundToInt

private fun plain(v: Double) = if (v == Math.floor(v) && v < 1e15) v.toLong().toString() else v.toString()

/** Settings of a savings widget: appearance switches, then the goal's content. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavingsConfigPanel(widgetId: Int, kind: WidgetKind, saveLabel: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(WidgetPrefs.load(context, widgetId, kind.defaultConfig)) }
    val s = cfg.savings
    fun update(change: (SavingsConfig) -> SavingsConfig) {
        cfg = cfg.copy(savings = change(cfg.savings))
    }

    var targetText by remember { mutableStateOf(plain(s.target)) }
    var savedText by remember { mutableStateOf(plain(s.saved)) }
    var adjusting by remember { mutableStateOf(false) }
    // the previous background is only deleted once the new settings are saved
    val oldBg = remember { s.bgFile }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = SavingsStore.saveBackground(context, uri)
            if (name != null) {
                if (cfg.savings.bgFile != oldBg) SavingsStore.deleteBackground(context, cfg.savings.bgFile)
                update { it.copy(bgFile = name) }
            }
        }
    }

    val preview = remember(cfg) { kind.render(context, cfg, 900, 566) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "Widget preview",
            modifier = Modifier.fillMaxWidth().aspectRatio(900f / 566f),
        )

        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().clickable { picker.launch("image/*") },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Set background image", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (s.bgFile.isEmpty()) "Choose a photo from your gallery" else "Tap to change the photo",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (s.bgFile.isNotEmpty()) {
                TextButton(onClick = {
                    if (cfg.savings.bgFile != oldBg) SavingsStore.deleteBackground(context, cfg.savings.bgFile)
                    update { it.copy(bgFile = "") }
                }) { Text("Remove") }
            }
        }
        SwitchRow("Expressive style", "Use wavy shapes instead of standard linear ones", s.expressive) { v -> update { it.copy(expressive = v) } }
        SwitchRow("Quick calculator", "Make the emoji tappable for quick amount adjustment via a calculator", s.quickCalc) { v -> update { it.copy(quickCalc = v) } }
        SwitchRow("Monthly progress", "Display progress added this month", s.monthly) { v -> update { it.copy(monthly = v) } }
        SwitchRow("Debt mode", "Show the remaining amount as the main value", s.debtMode) { v -> update { it.copy(debtMode = v) } }
        SwitchRow("Estimated time", "Display approximate months remaining to reach the goal (visible only if there is progress this month)", s.estimated) { v -> update { it.copy(estimated = v) } }
        SwitchRow("Monochrome emoji", "Make the emoji match the widget's text color", s.monoEmoji) { v -> update { it.copy(monoEmoji = v) } }

        Text("Colors", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Palettes.options.forEach { option ->
                FilterChip(
                    selected = cfg.palette == option.id,
                    onClick = { cfg = cfg.copy(palette = option.id) },
                    label = { Text(option.label) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        NumberSlider("Roundness", (cfg.roundness * 100).roundToInt(), 0..100) { cfg = cfg.copy(roundness = it / 100f) }

        Text("Content", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = s.name,
            onValueChange = { v -> update { it.copy(name = v.take(40)) } },
            label = { Text("Goal name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = s.emoji,
                onValueChange = { v -> update { it.copy(emoji = v.take(8)) } },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = s.currency,
                onValueChange = { v -> update { it.copy(currency = v.take(4)) } },
                label = { Text("Currency") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = targetText,
            onValueChange = { v ->
                val t = v.filter { it.isDigit() || it == '.' }
                targetText = t
                update { it.copy(target = t.toDoubleOrNull() ?: 0.0) }
            },
            label = { Text("Target amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = savedText,
            onValueChange = { v ->
                val t = v.filter { it.isDigit() || it == '.' }
                savedText = t
                update { it.copy(saved = t.toDoubleOrNull() ?: 0.0) }
            },
            label = { Text("Saved amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                savedText = "0"
                update { it.copy(saved = 0.0, monthStart = 0.0) }
            }) { Text("Reset") }
            OutlinedButton(onClick = { adjusting = true }) { Text("+ Adjust") }
            Button(
                onClick = {
                    if (cfg.savings.bgFile != oldBg) SavingsStore.deleteBackground(context, oldBg)
                    WidgetPrefs.save(context, widgetId, cfg.copy(savings = cfg.savings.rolled()))
                    onSaved()
                },
                modifier = Modifier.weight(1f),
            ) { Text(saveLabel) }
        }
    }

    if (adjusting) {
        AdjustDialog(
            currency = s.currency,
            saved = s.saved,
            onApply = { v ->
                val n = maxOf(0.0, v)
                savedText = plain(n)
                update { it.copy(saved = n) }
                adjusting = false
            },
            onDismiss = { adjusting = false },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
