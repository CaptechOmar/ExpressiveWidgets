package com.captechomar.widgets.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlin.random.Random

private val hint @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiceTool() {
    var count by rememberSaveable { mutableIntStateOf(2) }
    var values by rememberSaveable { mutableStateOf(listOf(0, 0, 0, 0)) }
    var rolling by remember { mutableStateOf(false) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs { Choice(listOf("1", "2", "3", "4"), count - 1) { count = it + 1 } }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until count) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(104.dp).rotate(if (rolling) spin.value * (if (i % 2 == 0) 1 else -1) else 0f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (values[i] == 0) "?" else values[i].toString(), style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        if (values.take(count).all { it > 0 }) ResultCard("Total", values.take(count).sum().toString())
        Button(
            onClick = {
                scope.launch {
                    rolling = true
                    spin.snapTo(0f)
                    launch { spin.animateTo(360f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) }
                    repeat(8) {
                        values = List(4) { Random.nextInt(1, 7) }
                        delay(60)
                    }
                    rolling = false
                }
            },
            enabled = !rolling,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Roll", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun CoinTool() {
    var side by rememberSaveable { mutableStateOf("") }
    var flipping by remember { mutableStateOf(false) }
    val turn = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(180.dp).rotate(turn.value)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (flipping) "…" else side.ifEmpty { "?" }, style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Button(
            onClick = {
                scope.launch {
                    flipping = true
                    turn.snapTo(0f)
                    turn.animateTo(720f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow))
                    side = if (Random.nextBoolean()) "Heads" else "Tails"
                    turn.snapTo(0f)
                    flipping = false
                }
            },
            enabled = !flipping,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Flip", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun NumberTool() {
    var min by rememberSaveable { mutableStateOf("1") }
    var max by rememberSaveable { mutableStateOf("100") }
    var result by rememberSaveable { mutableStateOf<Int?>(null) }
    val lo = min.toNum()?.toLong()
    val hi = max.toNum()?.toLong()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs { Row2({ NumField("From", min, { v -> min = v }, it) }, { NumField("To", max, { v -> max = v }, it) }) }
        result?.let { ResultCard("Your number", it.toString()) }
        Button(
            onClick = { if (lo != null && hi != null && lo <= hi) result = Random.nextLong(lo, hi + 1).toInt() },
            enabled = lo != null && hi != null && lo <= hi,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Generate", style = MaterialTheme.typography.titleMedium) }
        if (lo != null && hi != null && lo > hi) Text("The first number must not be larger than the second.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun PickerTool() {
    var text by rememberSaveable { mutableStateOf("") }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    val options = text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Inputs {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text("Options, one per line") },
                shape = RoundedCornerShape(18.dp), minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
        }
        pick?.let { ResultCard("Chosen", it) }
        Button(
            onClick = { pick = options.randomOrNull() },
            enabled = options.size >= 2,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Pick one", style = MaterialTheme.typography.titleMedium) }
        if (options.size < 2) Text("Add at least two options.", style = MaterialTheme.typography.bodyMedium, color = hint, modifier = Modifier.padding(start = 4.dp))
    }
}
