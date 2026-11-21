package com.captechomar.widgets.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.data.TapAction

/** Which apps the "open an app" list offers, and how the default reads. */
enum class TapPick(val base: Intent?, val defaultText: String, val describeDefault: String, val openText: String) {
    ANY(null, "Open this app (default)", "Opens this app", "Open an app…"),
    CLOCK(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), "Open the clock app (default)", "Opens your clock app", "Choose a clock app…"),
    CALENDAR(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), "Open the calendar app (default)", "Opens your calendar app", "Choose a calendar app…"),
}

/** Shows what a block does when tapped and lets the user change it: default, nothing, an app, an activity or a shortcut. */
@Composable
fun TapActionEditor(action: TapAction, onChange: (TapAction) -> Unit, pick: TapPick = TapPick.ANY) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var activityDialog by remember { mutableStateOf(false) }
    // 0 = picking an app, 1 = picking a shortcut provider, 2 = running the shortcut's own setup screen
    var stage by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val holder = remember { arrayOfNulls<ManagedActivityResultLauncher<Intent, ActivityResult>>(1) }

    fun pickerFor(base: Intent) = Intent(Intent.ACTION_PICK_ACTIVITY).putExtra(Intent.EXTRA_INTENT, base)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        when (stage) {
            0 -> {
                val component = data.component ?: return@rememberLauncherForActivityResult
                val launch = (pick.base?.let { Intent(it) } ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).setComponent(component)
                onChange(TapAction.intent(launch, labelOf(context, component)))
                menu = false
            }
            1 -> {
                stage = 2
                try {
                    holder[0]?.launch(data)
                } catch (_: Throwable) {
                    error = "That app can't create shortcuts here."
                }
            }
            else -> {
                @Suppress("DEPRECATION")
                val shortcut = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
                val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                if (shortcut != null) {
                    onChange(TapAction.intent(shortcut, name))
                    menu = false
                } else {
                    error = "That shortcut has no action to run."
                }
            }
        }
    }
    holder[0] = launcher

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(describe(action, pick), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { menu = true }) { Text("Change") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    if (menu && !activityDialog) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("When tapped") },
            text = {
                Column {
                    Option(pick.defaultText) { onChange(TapAction()); menu = false }
                    Option("Do nothing") { onChange(TapAction(TapAction.Type.NONE)); menu = false }
                    Option(pick.openText) {
                        error = null; stage = 0
                        launcher.launch(pickerFor(pick.base ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
                    }
                    if (pick == TapPick.ANY) {
                        Option("Open an activity…") { activityDialog = true }
                        Option("Run a shortcut…") {
                            error = null; stage = 1
                            launcher.launch(pickerFor(Intent(Intent.ACTION_CREATE_SHORTCUT)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("Close") } },
        )
    }

    if (menu && activityDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { activityDialog = false },
            title = { Text("Open an activity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter package/class, for example com.android.settings/.Settings", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = text, onValueChange = { text = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.contains('/'),
                    onClick = {
                        val component = ComponentName.unflattenFromString(text)
                        if (component != null) onChange(TapAction.intent(Intent().setComponent(component), component.shortClassName))
                        activityDialog = false
                        menu = false
                    },
                ) { Text("Use") }
            },
            dismissButton = { TextButton(onClick = { activityDialog = false }) { Text("Back") } },
        )
    }
}

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}

private fun describe(action: TapAction, pick: TapPick) = when (action.type) {
    TapAction.Type.DEFAULT -> pick.describeDefault
    TapAction.Type.NONE -> "Does nothing"
    TapAction.Type.INTENT -> action.label.ifBlank { "Custom" }
}

private fun labelOf(context: Context, component: ComponentName): String = try {
    context.packageManager.getActivityInfo(component, 0).loadLabel(context.packageManager).toString()
} catch (_: Throwable) {
    component.shortClassName
}
