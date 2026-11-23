package com.captechomar.widgets.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import com.captechomar.widgets.R
import com.captechomar.widgets.ui.Container

private const val FAVORITES = "favorites"

/** A tool together with the category it lives in; [key] is how favorites and navigation refer to it. */
private class Entry(val tool: Tool, val category: ToolCategory) {
    val key = "${category.id}/${tool.id}"
}

/**
 * The Tools tab: category tiles, then a category's tools, then one tool. Back steps out one level.
 * [onMode] says what is open so the bottom bar can hide: 0 nothing, 1 a tool, 2 a full-screen tool.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolsTab(onMode: (Int) -> Unit) {
    val favorites = rememberFavorites()
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var toolKey by rememberSaveable { mutableStateOf<String?>(null) }

    val everything = ToolCategories.all.flatMap { c -> c.tools.map { Entry(it, c) } }
    val favoriteEntries = everything.filter { it.key in favorites.keys }
    val favoritesCategory = ToolCategory(FAVORITES, "Favorites", "Your starred tools", R.drawable.tool_star_filled, MaterialShapes.Gem, favoriteEntries.map { it.tool })

    // if the last favorite is removed while its page is open, go back to the tiles
    LaunchedEffect(favoriteEntries.isEmpty(), categoryId) {
        if (categoryId == FAVORITES && favoriteEntries.isEmpty()) {
            categoryId = null
            toolKey = null
        }
    }

    val category = if (categoryId == FAVORITES) favoritesCategory.takeIf { favoriteEntries.isNotEmpty() } else ToolCategories.all.firstOrNull { it.id == categoryId }
    val rows = when {
        category == null -> emptyList()
        category.id == FAVORITES -> favoriteEntries
        else -> category.tools.map { Entry(it, category) }
    }
    val open = everything.firstOrNull { it.key == toolKey }?.takeIf { category != null }

    SideEffect { onMode(if (open == null) 0 else if (open.tool.fullScreen) 2 else 1) }
    DisposableEffect(Unit) { onDispose { onMode(0) } }
    BackHandler(enabled = category != null) { if (open != null) toolKey = null else categoryId = null }

    if (open != null && open.tool.fullScreen) {
        Box(Modifier.fillMaxSize()) {
            open.tool.content()
            // floating back and favorite buttons
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp),
            ) {
                Row {
                    FavoriteButton(open.key in favorites.keys) { favorites.toggle(open.key) }
                    IconButton(onClick = { toolKey = null }) {
                        Icon(painterResource(R.drawable.tool_back), contentDescription = "Back")
                    }
                }
            }
        }
        return
    }

    if (open != null && open.tool.fill) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(open.tool.title, onBack = { toolKey = null }) {
                FavoriteButton(open.key in favorites.keys) { favorites.toggle(open.key) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { open.tool.content() }
        }
        return
    }

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
                val tiles = (if (favoriteEntries.isNotEmpty()) listOf(favoritesCategory) else emptyList()) + ToolCategories.all
                tiles.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { col, c ->
                            CategoryTile(c, row * 2 + col, Modifier.weight(1f)) { categoryId = c.id }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            open == null -> {
                PageHeader(category.title, onBack = { categoryId = null })
                rows.forEachIndexed { i, e ->
                    Container(Modifier.clip(RoundedCornerShape(28.dp)).clickable { toolKey = e.key }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ToolShape(e.tool.iconRes, i, ROW_SHAPES[i % ROW_SHAPES.size], 56)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(e.tool.title, style = MaterialTheme.typography.titleLarge)
                                Text(e.tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            else -> {
                PageHeader(open.tool.title, onBack = { toolKey = null }) {
                    FavoriteButton(open.key in favorites.keys) { favorites.toggle(open.key) }
                }
                open.tool.content()
            }
        }
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (favorite) R.drawable.tool_star_filled else R.drawable.tool_star),
            contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.tool_back), contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp).weight(1f))
        trailing()
    }
}

/** The shapes behind the tool rows, in the order they are handed out: angular and soft ones, with few cookies. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ROW_SHAPES: List<RoundedPolygon> = listOf(
    MaterialShapes.Slanted, MaterialShapes.Pill, MaterialShapes.Bun, MaterialShapes.Pentagon, MaterialShapes.Arch,
    MaterialShapes.Fan, MaterialShapes.Gem, MaterialShapes.Ghostish, MaterialShapes.Cookie6Sided,
)

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
