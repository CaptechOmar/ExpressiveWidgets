package com.captechomar.widgets.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import com.captechomar.widgets.R
import com.captechomar.widgets.data.WidgetPrefs
import kotlin.math.max
import kotlin.math.min

object WidgetUpdater {

    fun updateAll(context: Context) {
        WidgetKinds.all.forEach { updateKind(context, it) }
    }

    fun updateKind(context: Context, kind: WidgetKind) {
        val app = context.applicationContext
        val awm = AppWidgetManager.getInstance(app)
        for (id in awm.getAppWidgetIds(ComponentName(app, kind.providerClass))) {
            update(app, awm, kind, id, force = false)
        }
    }

    fun update(context: Context, kind: WidgetKind, widgetId: Int) {
        val app = context.applicationContext
        update(app, AppWidgetManager.getInstance(app), kind, widgetId)
    }

    /** What was last pushed to each widget. A refresh that would change nothing is skipped, so a widget isn't redrawn under the launcher mid-animation. */
    private val last = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private fun update(context: Context, awm: AppWidgetManager, kind: WidgetKind, widgetId: Int, force: Boolean = true) {
        try {
            val size = sizeOf(context, awm, widgetId)
            val cfg = WidgetPrefs.load(context, widgetId, kind.defaultConfig)
            val bitmap = kind.render(context, cfg, size.wPx, size.hPx)
            val key = run {
                val buf = java.nio.ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buf)
                "${kind.title}|$cfg|${size.wDp}x${size.hDp}|${bitmap.width}x${bitmap.height}|${buf.array().contentHashCode()}"
            }
            if (!force && last[widgetId] == key) return
            last[widgetId] = key
            val views = RemoteViews(context.packageName, kind.layoutRes)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            kind.bindClicks(context, views, widgetId, cfg, size.wDp, size.hDp)
            awm.updateAppWidget(widgetId, views)
        } catch (_: Throwable) {
            // never let one widget crash the receiver
        }
    }

    /** Height divided by width of a placed widget. */
    fun aspect(context: Context, widgetId: Int): Float {
        val s = sizeOf(context, AppWidgetManager.getInstance(context.applicationContext), widgetId)
        return (s.hDp / s.wDp).coerceIn(0.3f, 2f)
    }

    private class Size(val wPx: Int, val hPx: Int, val wDp: Float, val hDp: Float)

    /** Widget size, with the bitmap size scaled down so it stays well under RemoteViews' memory limit. */
    private fun sizeOf(context: Context, awm: AppWidgetManager, widgetId: Int): Size {
        val o = awm.getAppWidgetOptions(widgetId)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wDp = if (landscape) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
        else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val hDp = if (landscape) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
        else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 56)
        val density = context.resources.displayMetrics.density
        var w = max(wDp * density, 100f)
        var h = max(hDp * density, 40f)
        val scale = min(1f, 900f / w)
        w *= scale
        h *= scale
        return Size(w.toInt(), h.toInt(), wDp.toFloat(), hDp.toFloat())
    }
}
