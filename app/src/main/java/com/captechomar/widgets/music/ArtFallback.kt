package com.captechomar.widgets.music

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import kotlin.math.max

/**
 * Picture the music widget shows when a track has no album art: the device wallpaper (when Android lets the app
 * read it) or a picture the user picked. Both are kept as small files so drawing the widget stays cheap.
 */
object ArtFallback {
    const val ICON = "icon"
    const val WALLPAPER = "wallpaper"
    const val PICTURE = "picture"

    private const val MAX_SIDE = 640

    private fun wallpaperFile(context: Context) = File(context.applicationContext.filesDir, "fallback_wallpaper.png")
    private fun pictureFile(context: Context) = File(context.applicationContext.filesDir, "fallback_picture.png")

    fun hasWallpaper(context: Context) = wallpaperFile(context).exists()
    fun hasPicture(context: Context) = pictureFile(context).exists()

    /** The bitmap for [mode], or null to show the plain music note. The wallpaper falls back to the picked picture. */
    fun load(context: Context, mode: String): Bitmap? = try {
        when (mode) {
            WALLPAPER -> decode(wallpaperFile(context)) ?: decode(pictureFile(context))
            PICTURE -> decode(pictureFile(context))
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    private fun decode(file: File): Bitmap? = file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Copies the current wallpaper into the app. Returns false when Android doesn't let us read it. */
    fun refreshWallpaper(context: Context): Boolean = try {
        val wm = WallpaperManager.getInstance(context.applicationContext)
        val drawable = wm.drawable ?: wm.peekDrawable()
        val bmp = drawable?.toBitmap()
        if (bmp == null) false else save(bmp, wallpaperFile(context))
    } catch (_: Throwable) {
        false
    }

    /** Stores the picture chosen in the photo picker. */
    fun savePicture(context: Context, uri: Uri): Boolean = try {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (bmp == null) false else save(bmp, pictureFile(context))
    } catch (_: Throwable) {
        false
    }

    private fun save(bitmap: Bitmap, file: File): Boolean = try {
        val scale = MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (_: Throwable) {
        false
    }
}
