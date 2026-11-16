package com.captechomar.widgets.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.widgets.BatteryWidgetProvider
import com.captechomar.widgets.widgets.WidgetUpdater

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Expressive Widgets", style = MaterialTheme.typography.headlineLarge)
                        AddWidgetCard()
                        WidgetList(refresh)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
        WidgetUpdater.updateAll(this)
    }
}

@Composable
private fun AddWidgetCard() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Batteries", style = MaterialTheme.typography.titleLarge)
            Text("Phone and Bluetooth earphones battery on your home screen.", style = MaterialTheme.typography.bodyMedium)
            val awm = AppWidgetManager.getInstance(context)
            if (awm.isRequestPinAppWidgetSupported) {
                Button(onClick = {
                    awm.requestPinAppWidget(ComponentName(context, BatteryWidgetProvider::class.java), null, null)
                }) { Text("Add to home screen") }
            } else {
                Text("Long-press your home screen, open Widgets and pick Expressive Widgets.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WidgetList(refresh: Int) {
    val context = LocalContext.current
    val ids = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
        .toList()
    @Suppress("UNUSED_EXPRESSION") refresh
    if (ids.isEmpty()) {
        Text("No widgets placed yet.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text("Your widgets", style = MaterialTheme.typography.titleMedium)
    ids.forEachIndexed { index, id ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Batteries #${index + 1}", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = {
                    context.startActivity(
                        Intent(context, WidgetConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    )
                }) { Text("Customize") }
            }
        }
    }
}
