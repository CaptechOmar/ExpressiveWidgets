package com.captechomar.widgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import com.captechomar.widgets.data.TapAction
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.ShortcutsKind
import com.captechomar.widgets.widgets.openable

/** Opened by a tap on a shortcut tile. It has no screen: it gives a haptic tick, opens what the tile points at and closes. */
class ShortcutTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val item = WidgetPrefs.load(this, widgetId, ShortcutsKind.defaultConfig).shortcuts.items.getOrNull(intent.getIntExtra("index", -1))
        if (item != null) {
            tick()
            val target = (if (item.action.type == TapAction.Type.INTENT) item.action.intent()?.let { openable(this, it) } else null)
                ?: Intent(this, MainActivity::class.java)
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {
                Toast.makeText(this, "Couldn't open that shortcut.", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun tick() {
        try {
            val v = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (_: Throwable) {
        }
    }
}
