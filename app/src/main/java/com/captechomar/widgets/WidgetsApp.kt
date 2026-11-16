package com.captechomar.widgets

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.captechomar.widgets.widgets.UpdateWorker
import com.captechomar.widgets.widgets.WidgetUpdater

class WidgetsApp : Application() {
    private var lastKey = ""

    override fun onCreate() {
        super.onCreate()
        UpdateWorker.schedule(this)
        // Live updates while the process is alive. The system never delivers
        // ACTION_BATTERY_CHANGED to manifest receivers, so this is registered at runtime.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val key = "$level/$plugged"
                if (key != lastKey) {
                    lastKey = key
                    WidgetUpdater.updateAll(context)
                }
            }
        }
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}
