package com.captechomar.widgets.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // the big pill squares off a little while it is held down
    val radius by androidx.compose.animation.core.animateDpAsState(if (pressed) 30.dp else 60.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "radius")
    Column(
        Modifier.fillMaxWidth().heightIn(min = 560.dp),
        verticalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the coin turns end over end in 3D while the result is being decided
        val frontUp = kotlin.math.cos(Math.toRadians(turn.value.toDouble())) >= 0.0
        val lift = kotlin.math.sin(Math.PI * (turn.value / 2160f).coerceIn(0f, 1f)).toFloat()
        Box(
            Modifier
                .size(200.dp)
                .graphicsLayer {
                    rotationX = turn.value
                    cameraDistance = 14f * density
                    scaleX = 1f + 0.18f * lift
                    scaleY = 1f + 0.18f * lift
                }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (frontUp) {
                if (flipping) androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(com.captechomar.widgets.R.drawable.tool_coin), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(96.dp),
                ) else Text(side.ifEmpty { "?" }, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        Button(
            onClick = {
                scope.launch {
                    flipping = true
                    turn.snapTo(0f)
                    turn.animateTo(2160f, androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    side = if (Random.nextBoolean()) "Heads" else "Tails"
                    turn.snapTo(0f)
                    flipping = false
                }
            },
            enabled = !flipping,
            shape = RoundedCornerShape(radius),
            interactionSource = press,
            modifier = Modifier.fillMaxWidth(0.85f).height(120.dp),
        ) { Text("Flip", style = MaterialTheme.typography.headlineMedium) }
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

private val wheelColors @Composable get() = with(MaterialTheme.colorScheme) {
    listOf(primaryContainer, tertiaryContainer, secondaryContainer, errorContainer, primary, tertiary)
}

/** A wheel of your own choices: type them in, spin, and the pointer at the top picks one. */
@Composable
fun LuckyWheelTool() {
    var text by rememberSaveable { mutableStateOf("Pizza, Tacos, Sushi, Burgers, Pasta") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val options = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(12)
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = wheelColors
    val ink = MaterialTheme.colorScheme.onSurface
    val pointer = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Inputs {
            OutlinedTextField(
                value = text, onValueChange = { text = it; result = null },
                label = { Text("Options, separated by commas") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
        }
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(300.dp)) {
                val n = options.size.coerceAtLeast(1)
                val sweep = 360f / n
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 15.dp.toPx()
                    isFakeBoldText = true
                }
                for (i in 0 until n) {
                    val start = -90f + rotation.value + i * sweep
                    drawArc(colors[i % colors.size], start, sweep, true)
                    if (options.isNotEmpty()) {
                        drawIntoCanvas { c ->
                            c.nativeCanvas.save()
                            c.nativeCanvas.rotate(start + sweep / 2, center.x, center.y)
                            paint.color = ink.toArgb()
                            val label = options[i].let { if (it.length > 12) it.take(11) + "…" else it }
                            c.nativeCanvas.drawText(label, center.x + size.minDimension * 0.30f, center.y + paint.textSize * 0.35f, paint)
                            c.nativeCanvas.restore()
                        }
                    }
                }
                drawCircle(pointer, 18.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Fill)
                drawCircle(colors[0], 8.dp.toPx(), center)
            }
            // pointer at the top
            androidx.compose.foundation.Canvas(Modifier.size(300.dp)) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x - 14.dp.toPx(), -2.dp.toPx())
                    lineTo(center.x + 14.dp.toPx(), -2.dp.toPx())
                    lineTo(center.x, 26.dp.toPx())
                    close()
                }
                drawPath(p, pointer)
            }
        }
        result?.let { ResultCard("The wheel says", it) }
        Button(
            onClick = {
                scope.launch {
                    spinning = true
                    result = null
                    val target = rotation.value + 360f * Random.nextInt(5, 8) + Random.nextFloat() * 360f
                    rotation.animateTo(target, androidx.compose.animation.core.tween(4200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    val sweep = 360f / options.size
                    val a = ((-rotation.value) % 360f + 360f) % 360f
                    result = options[(a / sweep).toInt().coerceIn(0, options.size - 1)]
                    spinning = false
                }
            },
            enabled = !spinning && options.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (options.size < 2) "Add at least two options" else "Spin") }
    }
}

/** Spin the bottle: it turns a few times and slows to a stop, pointing at someone. */
@Composable
fun SpinBottleTool() {
    val angle = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sit in a circle, spin the bottle, and the neck points at the chosen one.", style = MaterialTheme.typography.bodyLarge, color = hint)
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(com.captechomar.widgets.R.drawable.tool_bottle), contentDescription = "Bottle",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(260.dp).rotate(angle.value),
            )
        }
        Button(
            onClick = {
                scope.launch {
                    spinning = true
                    val target = angle.value + 360f * Random.nextInt(4, 8) + Random.nextFloat() * 360f
                    angle.animateTo(target, androidx.compose.animation.core.tween(3800, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    spinning = false
                }
            },
            enabled = !spinning,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Spin the bottle") }
    }
}

/** Rock, paper or scissors: one button, one random pick shown as a single-colour icon. */
@Composable
fun RockPaperScissorsTool() {
    val names = listOf("Rock", "Paper", "Scissors")
    val icons = listOf(com.captechomar.widgets.R.drawable.rps_rock, com.captechomar.widgets.R.drawable.rps_paper, com.captechomar.widgets.R.drawable.rps_scissors)
    var pick by rememberSaveable { mutableIntStateOf(-1) }
    var shuffling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().height(280.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (pick < 0) Text("?", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                else androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(icons[pick]), contentDescription = names[pick],
                    tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(128.dp),
                )
                Text(if (pick < 0) " " else names[pick], style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(
            onClick = {
                scope.launch {
                    shuffling = true
                    repeat(8) { pick = Random.nextInt(3); delay(70) }
                    pick = Random.nextInt(3)
                    shuffling = false
                }
            },
            enabled = !shuffling,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Pick one", style = MaterialTheme.typography.titleMedium) }
    }
}
