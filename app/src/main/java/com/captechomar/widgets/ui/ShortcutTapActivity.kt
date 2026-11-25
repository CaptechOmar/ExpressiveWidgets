package com.captechomar.widgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.captechomar.widgets.data.TapAction
import com.captechomar.widgets.data.WidgetPrefs
import com.captechomar.widgets.widgets.ShortcutsAnim
import com.captechomar.widgets.widgets.ShortcutsKind
import com.captechomar.widgets.widgets.WidgetUpdater
import com.captechomar.widgets.widgets.openable

/**
 * Opened by a tap on a tile of the shortcuts widget. It has no screen: it closes the tile's corners, starts what the
 * tile opens, then lets the corners spring back while the other app is coming up.
 */
class ShortcutTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val index = intent.getIntExtra("index", -1)
        val item = WidgetPrefs.load(this, widgetId, ShortcutsKind.defaultConfig).shortcuts.items.getOrNull(index)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || item == null) {
            finish()
            return
        }
        val app = applicationContext
        val target = when (item.action.type) {
            TapAction.Type.INTENT -> item.action.intent()?.let { openable(app, it) }
            else -> null
        } ?: Intent(app, MainActivity::class.java)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Thread {
            fun frame(p: Float) {
                ShortcutsAnim.set(widgetId, index, p)
                WidgetUpdater.updateImage(app, ShortcutsKind, widgetId)
            }
            for (s in 1..5) {
                frame(s / 5f)
                Thread.sleep(35)
            }
            Thread.sleep(60)
            runOnUiThread {
                try {
                    startActivity(target)
                } catch (_: Throwable) {
                    Toast.makeText(app, "Couldn't open that shortcut.", Toast.LENGTH_SHORT).show()
                }
                finish()
                overridePendingTransition(0, 0)
            }
            Thread.sleep(300)
            for (s in 1..10) {
                val t = 1f - s / 10f
                frame(t * t)
                Thread.sleep(40)
            }
            ShortcutsAnim.set(widgetId, index, 0f)
            WidgetUpdater.update(app, ShortcutsKind, widgetId)
        }.start()
    }
}
