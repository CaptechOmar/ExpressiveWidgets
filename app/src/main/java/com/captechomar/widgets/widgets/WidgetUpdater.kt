package com.captechomar.widgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.captechomar.widgets.R
import com.captechomar.widgets.ui.MainActivity
import kotlin.math.max
import kotlin.math.min

object WidgetUpdater {

    fun updateAll(context: Context) {
        val app = context.applicationContext
        val awm = AppWidgetManager.getInstance(app)
        for (kind in WidgetKinds.all) {
            for (id in awm.getAppWidgetIds(ComponentName(app, kind.providerClass))) {
                update(app, awm, kind, id)
            }
        }
    }

    fun update(context: Context, kind: WidgetKind, widgetId: Int) {
        val app = context.applicationContext
        update(app, AppWidgetManager.getInstance(app), kind, widgetId)
    }

    private fun update(context: Context, awm: AppWidgetManager, kind: WidgetKind, widgetId: Int) {
        try {
            val (w, h) = sizePx(context, awm, widgetId)
            val bitmap = kind.render(context, widgetId, w, h)
            val views = RemoteViews(context.packageName, R.layout.widget_image)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            awm.updateAppWidget(widgetId, views)
        } catch (_: Throwable) {
            // never let one widget crash the receiver
        }
    }

    /** Widget size in pixels, scaled down so the bitmap stays well under RemoteViews' memory limit. */
    private fun sizePx(context: Context, awm: AppWidgetManager, widgetId: Int): Pair<Int, Int> {
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
        return w.toInt() to h.toInt()
    }
}
