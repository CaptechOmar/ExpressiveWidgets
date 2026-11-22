package com.captechomar.widgets.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.captechomar.widgets.ui.Container
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

/** The Tools tab: category tiles, then a category's tools, then one tool. Back steps out one level. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolsTab() {
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var toolId by rememberSaveable { mutableStateOf<String?>(null) }
    val category = ToolCategories.all.firstOrNull { it.id == categoryId }
    val tool = category?.tools?.firstOrNull { it.id == toolId }

    BackHandler(enabled = category != null) { if (tool != null) toolId = null else categoryId = null }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            category == null -> {
                Text("Tools", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 6.dp))
                Text(
                    "Handy things for every day. Pick a category.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
                ToolCategories.all.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { col, c ->
                            CategoryTile(c, row * 2 + col, Modifier.weight(1f)) { categoryId = c.id }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            tool == null -> {
                PageHeader(category.title) { categoryId = null }
                category.tools.forEachIndexed { i, t ->
                    Container(Modifier.clip(RoundedCornerShape(28.dp)).clickable { toolId = t.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ToolShape(t.iconRes, i, category.shape, 56)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleLarge)
                                Text(t.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            else -> {
                PageHeader(tool.title) { toolId = null }
                tool.content()
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(com.captechomar.widgets.R.drawable.tool_back), contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Six colour pairs so neighbouring tiles never match. */
@Composable
private fun pair(index: Int): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (index % 6) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.tertiaryContainer to c.onTertiaryContainer
        2 -> c.secondaryContainer to c.onSecondaryContainer
        3 -> c.primary to c.onPrimary
        4 -> c.tertiary to c.onTertiary
        else -> c.secondary to c.onSecondary
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryTile(category: ToolCategory, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = pair(index)
    Box(
        modifier.aspectRatio(1f).clip(category.shape.toShape()).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(26.dp)) {
            Icon(painterResource(category.iconRes), contentDescription = null, tint = fg, modifier = Modifier.size(40.dp))
            Text(category.title, style = MaterialTheme.typography.titleMedium, color = fg, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolShape(iconRes: Int, index: Int, polygon: RoundedPolygon, size: Int) {
    val (bg, fg) = pair(index + 1)
    Box(Modifier.size(size.dp).clip(polygon.toShape()).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(iconRes), contentDescription = null, tint = fg, modifier = Modifier.size((size * 0.45f).dp))
    }
}
