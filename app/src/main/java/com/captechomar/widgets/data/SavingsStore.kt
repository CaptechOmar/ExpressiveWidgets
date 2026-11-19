package com.captechomar.widgets.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.captechomar.widgets.widgets.SavingsKind
import com.captechomar.widgets.widgets.WidgetUpdater
import java.io.File
import kotlin.math.max

/** Changes to a savings widget that happen outside its settings screen (the quick calculator). */
object SavingsStore {

    /** Applies [change] to the widget's savings, saves it and redraws the widget. */
    fun mutate(context: Context, widgetId: Int, change: (SavingsConfig) -> SavingsConfig) {
        val app = context.applicationContext
        val cfg = WidgetPrefs.load(app, widgetId, SavingsKind.defaultConfig)
        WidgetPrefs.save(app, widgetId, cfg.copy(savings = change(cfg.savings)))
        WidgetUpdater.update(app, SavingsKind, widgetId)
    }

    private fun file(context: Context, name: String) = File(context.applicationContext.filesDir, name)

    fun loadBackground(context: Context, name: String): Bitmap? =
        if (name.isEmpty()) null else try {
            file(context, name).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        } catch (_: Throwable) {
            null
        }

    /** Copies a picked picture into the app, scaled down, and returns its file name. */
    fun saveBackground(context: Context, uri: Uri): String? = try {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scale = 1000f / max(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val name = "savings_bg_${System.currentTimeMillis()}.jpg"
        file(context, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        name
    } catch (_: Throwable) {
        null
    }

    fun deleteBackground(context: Context, name: String) {
        if (name.isNotEmpty()) file(context, name).delete()
    }
}
