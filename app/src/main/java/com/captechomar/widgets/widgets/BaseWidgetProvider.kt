package com.captechomar.widgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.captechomar.widgets.data.WidgetPrefs

abstract class BaseWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.update(context, kind, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        WidgetUpdater.update(context, kind, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        UpdateWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.delete(context, it) }
    }
}

class BatteryWidgetProvider : BaseWidgetProvider(BatteryKind)

class MusicWidgetProvider : BaseWidgetProvider(MusicKind)

class SavingsWidgetProvider : BaseWidgetProvider(SavingsKind)
