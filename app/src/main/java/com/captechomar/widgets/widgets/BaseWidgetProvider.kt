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

class DrawingWidgetProvider : BaseWidgetProvider(DrawingKind)

class WeatherWidgetProvider : BaseWidgetProvider(WeatherKind) {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherRefresh.enqueue(context)
        scheduleDateChange(context)
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        super.onReceive(context, intent)
        // the date is drawn into the bitmap, so redraw once the day changes
        if (intent.action == ACTION_REFRESH) {
            android.widget.Toast.makeText(context, "Updating weather…", android.widget.Toast.LENGTH_SHORT).show()
            WeatherRefresh.enqueue(context)
        }
        if (intent.action == ACTION_DATE_TICK) {
            WidgetUpdater.updateKind(context, WeatherKind)
            scheduleDateChange(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.captechomar.widgets.WEATHER_REFRESH"
        private const val ACTION_DATE_TICK = "com.captechomar.widgets.DATE_TICK"

        fun scheduleDateChange(context: Context) {
            val app = context.applicationContext
            val alarms = app.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val midnight = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 5)
            }.timeInMillis
            val pi = android.app.PendingIntent.getBroadcast(
                app, 77,
                android.content.Intent(app, WeatherWidgetProvider::class.java).setAction(ACTION_DATE_TICK),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC, midnight, pi)
        }
    }
}
