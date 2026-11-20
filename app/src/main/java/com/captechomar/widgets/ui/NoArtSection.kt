package com.captechomar.widgets.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.music.ArtFallback

/** "When there's no album art": the music note, the device wallpaper, or a picture of the user's choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoArtSection(mode: String, onMode: (String) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var wallpaperOk by remember { mutableStateOf(ArtFallback.hasWallpaper(context)) }
    var hasPicture by remember { mutableStateOf(ArtFallback.hasPicture(context)) }

    fun readWallpaper() {
        wallpaperOk = ArtFallback.refreshWallpaper(context) || ArtFallback.hasWallpaper(context)
        onChanged()
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { readWallpaper() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && ArtFallback.savePicture(context, uri)) {
            hasPicture = true
            onChanged()
        }
    }

    Section("When there's no album art") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == ArtFallback.ICON,
                onClick = { onMode(ArtFallback.ICON); onChanged() },
                label = { Text("Music note") },
                modifier = Modifier.padding(bottom = 4.dp),
            )
            FilterChip(
                selected = mode == ArtFallback.WALLPAPER,
                onClick = {
                    onMode(ArtFallback.WALLPAPER)
                    val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
                        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE) else readWallpaper()
                },
                label = { Text("Device wallpaper") },
                modifier = Modifier.padding(bottom = 4.dp),
            )
            FilterChip(
                selected = mode == ArtFallback.PICTURE,
                onClick = { onMode(ArtFallback.PICTURE); onChanged() },
                label = { Text("My picture") },
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (mode == ArtFallback.WALLPAPER && !wallpaperOk) {
            Text(
                "Android didn't let the app read your wallpaper. Pick a picture below and it is used instead.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (mode == ArtFallback.PICTURE || (mode == ArtFallback.WALLPAPER && !wallpaperOk)) {
            FilledTonalButton(onClick = { picker.launch("image/*") }) {
                Text(if (hasPicture) "Change picture" else "Choose picture")
            }
        }
    }
}
