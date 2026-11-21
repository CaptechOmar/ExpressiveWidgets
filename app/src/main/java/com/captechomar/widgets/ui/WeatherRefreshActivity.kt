package com.captechomar.widgets.ui

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.captechomar.widgets.data.WeatherStore
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.WeatherKind
import com.captechomar.widgets.widgets.WidgetUpdater

/**
 * Opened by tapping the weather icon on the widget. It has no screen: because it is in the foreground for a moment
 * it may read the device location with the normal "while using the app" permission, then it refreshes the weather
 * and closes. This is what lets "Use my location" work without "all the time" location access.
 */
class WeatherRefreshActivity : Activity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // this page has no screen, so it must never linger: it always closes within 5 seconds
        window.decorView.postDelayed({ if (!isFinishing) finish() }, 5000)
        val cfg = WidgetPrefs.load(this, widgetId, WeatherKind.defaultConfig)
        Toast.makeText(this, if (cfg.weather.useLocation) "Updating location and weather…" else "Updating weather…", Toast.LENGTH_SHORT).show()

        val permitted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cfg.weather.useLocation || !permitted) {
            refresh(widgetId, null)
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // a fresh fix; if none arrives within 3 seconds the last known one is used
            lm.getCurrentLocation(provider, null, mainExecutor) { loc: Location? -> refresh(widgetId, loc) }
            window.decorView.postDelayed({ refresh(widgetId, null) }, 3000)
        } else {
            refresh(widgetId, null)
        }
    }

    private fun refresh(widgetId: Int, loc: Location?) {
        if (done) return
        done = true
        val app = applicationContext
        Thread {
            val cfg = WidgetPrefs.load(app, widgetId, WeatherKind.defaultConfig)
            val updated = WeatherStore.refresh(app, cfg.weather, 0L, loc?.let { it.latitude to it.longitude })
            if (updated != cfg.weather) WidgetPrefs.save(app, widgetId, cfg.copy(weather = updated))
            WidgetUpdater.updateKind(app, WeatherKind)
            runOnUiThread { finish() }
        }.start()
    }
}
