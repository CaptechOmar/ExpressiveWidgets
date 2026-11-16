package com.captechomar.widgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.captechomar.widgets.data.WidgetPrefs

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.update(context, BatteryKind, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        WidgetUpdater.update(context, BatteryKind, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        UpdateWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.delete(context, it) }
    }
}
