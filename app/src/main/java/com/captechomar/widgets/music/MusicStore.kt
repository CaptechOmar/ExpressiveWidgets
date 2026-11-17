package com.captechomar.widgets.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationManagerCompat
import java.io.File
import kotlin.math.max

data class MusicState(
    val title: String,
    val artist: String,
    val packageName: String?,
    val playing: Boolean,
    val art: Bitmap?,
)

/** Last known playback state, written by [MediaListenerService] and read when drawing the widget. */
object MusicStore {
    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("music_state", Context.MODE_PRIVATE)

    private fun artFile(context: Context) = File(context.applicationContext.filesDir, "music_art.png")

    fun save(context: Context, title: String, artist: String, packageName: String?, playing: Boolean) {
        sp(context).edit()
            .putBoolean("has_state", true)
            .putString("title", title)
            .putString("artist", artist)
            .putString("pkg", packageName)
            .putBoolean("playing", playing)
            .apply()
    }

    fun hasArt(context: Context) = artFile(context).exists()

    fun saveArt(context: Context, bitmap: Bitmap?) {
        val file = artFile(context)
        if (bitmap == null) {
            file.delete()
            return
        }
        try {
            val scale = 512f / max(bitmap.width, bitmap.height)
            val out = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap
            file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Throwable) {
        }
    }

    fun load(context: Context): MusicState? {
        val sp = sp(context)
        if (!sp.getBoolean("has_state", false)) return null
        val art = try {
            artFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        } catch (_: Throwable) {
            null
        }
        return MusicState(
            title = sp.getString("title", "") ?: "",
            artist = sp.getString("artist", "") ?: "",
            packageName = sp.getString("pkg", null),
            playing = sp.getBoolean("playing", false),
            art = art,
        )
    }
}

object MediaAccess {
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
