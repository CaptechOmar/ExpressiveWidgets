package com.captechomar.widgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.widgets.BatteryKind
import com.captechomar.widgets.widgets.DrawingKind
import com.captechomar.widgets.widgets.SavingsKind
import com.captechomar.widgets.widgets.ShortcutsKind
import com.captechomar.widgets.widgets.WeatherKind
import com.captechomar.widgets.widgets.WidgetKinds
import com.captechomar.widgets.widgets.WidgetUpdater

/** Opened by the launcher when a widget is placed, and from the main screen to edit an existing one. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val kind = WidgetKinds.byProvider(
            AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        ) ?: BatteryKind
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    ) {
                        Text("${kind.title} widget", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
                        val onSaved: () -> Unit = {
                            WidgetUpdater.update(applicationContext, kind, widgetId)
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        }
                        if (kind === SavingsKind) SavingsConfigPanel(widgetId, kind, "Done", onSaved)
                        else if (kind === DrawingKind) DrawingConfigPanel(widgetId, kind, "Done", onSaved)
                        else if (kind === ShortcutsKind) ShortcutsConfigPanel(widgetId, kind, "Done", onSaved)
                        else if (kind === WeatherKind) WeatherConfigPanel(widgetId, kind, "Done", onSaved)
                        else ConfigPanel(widgetId = widgetId, kind = kind, saveLabel = "Done", onSaved = onSaved)
                    }
                }
            }
        }
    }
}
