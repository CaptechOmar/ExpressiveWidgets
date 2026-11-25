package com.captechomar.widgets.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.ShortcutFiles
import com.captechomar.widgets.data.ShortcutIcons
import com.captechomar.widgets.data.ShortcutItem
import com.captechomar.widgets.data.ShortcutsConfig
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.WidgetKind
import kotlin.math.roundToInt

/** Settings of a shortcuts widget: colours, and for each tile its icon and what it opens. */
@Composable
fun ShortcutsConfigPanel(widgetId: Int, kind: WidgetKind, saveLabel: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(WidgetPrefs.load(context, widgetId, kind.defaultConfig)) }
    val preview = remember(cfg) { kind.render(context, cfg, 960, 290) }
    var picking by remember { mutableStateOf(-1) }
    var uploadFor by remember { mutableStateOf(-1) }

    fun setItem(i: Int, item: ShortcutItem) {
        cfg = cfg.copy(shortcuts = ShortcutsConfig(cfg.shortcuts.items.mapIndexed { j, old -> if (j == i) item else old }))
    }

    val upload = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val i = uploadFor
        if (uri != null && i in cfg.shortcuts.items.indices) {
            ShortcutFiles.import(context, uri)?.let { setItem(i, cfg.shortcuts.items[i].copy(icon = it)) }
        }
        picking = -1
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(
            bitmap = preview.asImageBitmap(), contentDescription = "Widget preview",
            modifier = Modifier.fillMaxWidth().aspectRatio(960f / 290f),
        )
        Section("Colors") {
            PaletteRow(cfg.palette) { cfg = cfg.copy(palette = it) }
            NumberSlider("Roundness", (cfg.roundness * 100).roundToInt(), 0..100) { cfg = cfg.copy(roundness = it / 100f) }
        }
        cfg.shortcuts.items.forEachIndexed { i, item ->
            Section("Shortcut ${i + 1}") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBox(item, Modifier.size(56.dp))
                    FilledTonalButton(onClick = { picking = i }) { Text("Change icon") }
                    if (cfg.shortcuts.items.size > 1) {
                        TextButton(onClick = {
                            cfg = cfg.copy(shortcuts = ShortcutsConfig(cfg.shortcuts.items.filterIndexed { j, _ -> j != i }))
                        }) { Text("Remove") }
                    }
                }
                if (item.icon.startsWith("file:")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Match the widget colors", Modifier.weight(1f))
                        Switch(checked = item.tint, onCheckedChange = { setItem(i, item.copy(tint = it)) })
                    }
                }
                TapActionEditor(item.action, { setItem(i, item.copy(action = it)) })
            }
        }
        if (cfg.shortcuts.items.size < ShortcutsConfig.MAX) {
            OutlinedButton(
                onClick = {
                    val next = ShortcutIcons.all.firstOrNull { icon -> cfg.shortcuts.items.none { it.icon == icon.id } } ?: ShortcutIcons.all.first()
                    cfg = cfg.copy(shortcuts = ShortcutsConfig(cfg.shortcuts.items + ShortcutItem(next.id)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add a shortcut") }
        }
        Button(
            onClick = {
                WidgetPrefs.save(context, widgetId, cfg)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(saveLabel) }
    }

    if (picking >= 0) {
        AlertDialog(
            onDismissRequest = { picking = -1 },
            title = { Text("Choose an icon") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { uploadFor = picking; upload.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Upload your own…")
                    }
                    ShortcutIcons.all.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { icon ->
                                val current = cfg.shortcuts.items.getOrNull(picking)
                                Box(
                                    Modifier.size(58.dp).clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (current?.icon == icon.id) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        )
                                        .clickable {
                                            current?.let { setItem(picking, it.copy(icon = icon.id)) }
                                            picking = -1
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(icon.res), icon.label, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = -1 }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IconBox(item: ShortcutItem, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val file = if (item.icon.startsWith("file:")) remember(item.icon) { ShortcutFiles.bitmap(context, item.icon) } else null
        if (file != null) {
            Image(
                file.asImageBitmap(), null, Modifier.size(30.dp),
                colorFilter = if (item.tint) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
            )
        } else {
            ShortcutIcons.byId(item.icon)?.let {
                Icon(painterResource(it.res), it.label, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
