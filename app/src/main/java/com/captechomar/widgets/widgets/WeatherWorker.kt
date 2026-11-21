package com.captechomar.widgets.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.captechomar.widgets.data.WeatherStore
import com.captechomar.widgets.data.WidgetPrefs

/** Fetches the weather for every weather widget, then redraws them. */
object WeatherRefresh {
    /** Blocking: run from a background thread. Results younger than [maxAgeMs] are not fetched again. */
    fun all(context: Context, maxAgeMs: Long) {
        val app = context.applicationContext
        val awm = AppWidgetManager.getInstance(app)
        val ids = awm.getAppWidgetIds(ComponentName(app, WeatherKind.providerClass))
        if (ids.isEmpty()) return
        for (id in ids) {
            val cfg = WidgetPrefs.load(app, id, WeatherKind.defaultConfig)
            val updated = WeatherStore.refresh(app, cfg.weather, maxAgeMs)
            // the device position is remembered, so it keeps working when the app is in the background
            if (updated != cfg.weather) WidgetPrefs.save(app, id, cfg.copy(weather = updated))
        }
        WidgetUpdater.updateKind(app, WeatherKind)
    }

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("weather-refresh", ExistingWorkPolicy.REPLACE, request)
    }
}

class WeatherWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        WeatherRefresh.all(applicationContext, 0L)
        return Result.success()
    }
}
