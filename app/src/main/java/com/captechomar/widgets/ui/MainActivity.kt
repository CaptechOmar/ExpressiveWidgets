package com.captechomar.widgets.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import com.captechomar.widgets.R
import com.captechomar.widgets.ui.tools.ToolsTab
import com.captechomar.widgets.ui.tools.Choice
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.graphics.shapes.RoundedPolygon
import kotlin.random.Random
import com.captechomar.widgets.widgets.WidgetKind
import com.captechomar.widgets.widgets.WidgetKinds
import com.captechomar.widgets.widgets.WidgetUpdater

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    private var pendingTool by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pendingTool = intent.getStringExtra("open_tool")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingTool = intent?.getStringExtra("open_tool")
        setContent {
            AppTheme {
                var tab by rememberSaveable { mutableIntStateOf(1) }
                // what the Tools tab has open: 0 nothing, 1 a tool, 2 a full-screen tool (the ruler)
                var toolMode by remember { mutableIntStateOf(0) }
                val inTool = tab == 1 && toolMode != 0
                LaunchedEffect(pendingTool) { if (pendingTool != null) tab = 1 }
                var searching by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                  Box(Modifier.fillMaxSize()) {
                    Box(
                        if (tab == 1 && toolMode == 2) Modifier
                        else Modifier.systemBarsPadding().padding(bottom = if (inTool) 0.dp else 84.dp),
                    ) {
                        if (tab == 0) WidgetsTab(refresh) else ToolsTab(pendingTool, { pendingTool = null }) { toolMode = it }
                    }
                    if (!inTool) {
                        // floating navigation, styled like the ruler's cm/in and calibrate bar
                        Row(
                            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
                                Row(Modifier.padding(10.dp)) {
                                    Choice(
                                        listOf("Widgets", "Tools"), tab, Modifier.width(260.dp),
                                        icons = listOf(R.drawable.tab_widgets to R.drawable.tab_widgets_fill, R.drawable.tab_tools to R.drawable.tab_tools_fill),
                                    ) { tab = it }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 8.dp,
                                modifier = Modifier.size(width = 60.dp, height = 60.dp).clip(RoundedCornerShape(22.dp)).clickable { searching = true },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.sci_lucide_search), contentDescription = "Search tools", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                    if (searching) ToolSearch(onDismiss = { searching = false }) { key -> searching = false; pendingTool = key }
                  }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
        com.captechomar.widgets.music.ArtFallback.refreshWallpaper(this)
        WidgetUpdater.updateAll(this)
    }
}

@Composable
private fun WidgetsTab(refresh: Int) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Widgets", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 6.dp))
        Text(
            "Tap Add, or long-press your home screen \u2192 Widgets. Long-press a widget to change its shape and colours.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Text("Add a widget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
        // a new random shape for every widget each time the app is opened
        val seed = rememberSaveable { Random.nextInt() }
        val shapes = remember(seed) { SHAPES.shuffled(Random(seed)) }
        WidgetKinds.all.forEachIndexed { i, kind -> AddWidgetCard(kind, i, shapes[i % shapes.size]) }
        WidgetList(refresh)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SHAPES: List<RoundedPolygon> = listOf(
    MaterialShapes.Arch, MaterialShapes.Fan, MaterialShapes.Slanted, MaterialShapes.Pill, MaterialShapes.Bun,
    MaterialShapes.Ghostish, MaterialShapes.Gem, MaterialShapes.Pentagon, MaterialShapes.Cookie6Sided,
)

/** The shape behind a widget's icon: backgrounds alternate primary, secondary and tertiary. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeIcon(iconRes: Int, index: Int, polygon: RoundedPolygon, size: Dp = 72.dp) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (index % 3) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.secondaryContainer to c.onSecondaryContainer
        else -> c.tertiary to c.onTertiary
    }
    Box(Modifier.size(size).clip(polygon.toShape()).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(iconRes), contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
private fun AddWidgetCard(kind: WidgetKind, index: Int, polygon: RoundedPolygon) {
    val context = LocalContext.current
    val awm = AppWidgetManager.getInstance(context)
    Container {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShapeIcon(kind.iconRes, index, polygon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kind.title, style = MaterialTheme.typography.titleLarge)
                Text(kind.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (awm.isRequestPinAppWidgetSupported) {
                FilledTonalButton(
                    onClick = { awm.requestPinAppWidget(ComponentName(context, kind.providerClass), null, null) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Text("Add", style = MaterialTheme.typography.titleMedium) }
            }
        }
        if (!awm.isRequestPinAppWidgetSupported) {
            Text("Long-press your home screen, open Widgets and pick Warsha.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WidgetList(refresh: Int) {
    val context = LocalContext.current
    val awm = AppWidgetManager.getInstance(context)
    val placed = if (refresh >= 0) {
        WidgetKinds.all.flatMap { kind ->
            awm.getAppWidgetIds(ComponentName(context, kind.providerClass)).mapIndexed { i, id -> Triple(kind, i + 1, id) }
        }
    } else emptyList()

    Text("Your widgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
    if (placed.isEmpty()) {
        Container { Text("No widgets placed yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    placed.forEach { (kind, number, id) ->
        Container {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${kind.title} #$number", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    context.startActivity(
                        Intent(context, WidgetConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    )
                }) { Text("Customize", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
