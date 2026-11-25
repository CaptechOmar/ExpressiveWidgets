package com.captechomar.widgets.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/** One tile of the shortcuts widget: an icon (a library id, or "file:name" for an uploaded picture) and what it opens. */
data class ShortcutItem(
    val icon: String,
    /** Colour an uploaded picture like the library icons (it is used as a mask). */
    val tint: Boolean = true,
    val action: TapAction = TapAction(),
)

data class ShortcutsConfig(val items: List<ShortcutItem> = default()) {
    fun toJson(): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("icon", it.icon).put("tint", it.tint).put("action", it.action.toJson()))
        }
        return arr.toString()
    }

    companion object {
        const val MAX = 6

        fun default() = listOf(
            "solar:bolt-bold", "tabler:square-rounded-check", "cib:stadia", "lucide:circle-dollar-sign", "tabler:device-remote",
        ).map { ShortcutItem(it) }

        fun fromJson(s: String?): ShortcutsConfig {
            if (s == null) return ShortcutsConfig()
            return try {
                val arr = JSONArray(s)
                ShortcutsConfig((0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ShortcutItem(
                        o.getString("icon"), o.optBoolean("tint", true),
                        o.optJSONObject("action")?.let(TapAction::fromJson) ?: TapAction(),
                    )
                }.take(MAX))
            } catch (_: Throwable) {
                ShortcutsConfig()
            }
        }
    }
}

/** Pictures the user uploaded as shortcut icons. */
object ShortcutFiles {
    private fun dir(context: Context) = File(context.filesDir, "shortcut_icons").apply { mkdirs() }

    fun file(context: Context, id: String): File? =
        if (id.startsWith("file:")) File(dir(context), id.removePrefix("file:")) else null

    fun bitmap(context: Context, id: String): Bitmap? =
        file(context, id)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Saves a picked picture scaled to at most 192 px and returns its id, or null when it can't be read. */
    fun import(context: Context, uri: Uri): String? = try {
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val k = 192f / max(src.width, src.height)
        val bmp = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true) else src
        val name = "i${System.currentTimeMillis()}.png"
        File(dir(context), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "file:$name"
    } catch (_: Throwable) {
        null
    }

    fun delete(context: Context, id: String) {
        file(context, id)?.delete()
    }
}
