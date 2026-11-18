package com.captechomar.widgets.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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

/** One block of the widget that can be given a tap action: its key in the config and what to call it. */
data class TapBlock(val key: String, val label: String)

/** "Tap actions" settings: for each block pick the default, nothing, an app, an activity or a shortcut. */
@Composable
fun TapActionSection(blocks: List<TapBlock>, actions: Map<String, TapAction>, onChange: (String, TapAction) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }
    var activityDialog by remember { mutableStateOf(false) }
    // 0 = picking an app, 1 = picking a shortcut provider, 2 = running the shortcut's own setup screen
    var stage by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickerFor(base: Intent) = Intent(Intent.ACTION_PICK_ACTIVITY).putExtra(Intent.EXTRA_INTENT, base)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val key = editing
        val data = result.data
        if (key == null || result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        when (stage) {
            0 -> {
                val component = data.component ?: return@rememberLauncherForActivityResult
                val label = labelOf(context, component)
                val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                onChange(key, TapAction.intent(launch, label))
            }
            1 -> {
                stage = 2
                try {
                    launcherRef?.launch(data)
                } catch (_: Throwable) {
                    error = "That app can't create shortcuts here."
                }
            }
            else -> {
                @Suppress("DEPRECATION")
                val shortcut = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
                val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                if (shortcut != null) onChange(key, TapAction.intent(shortcut, name))
                else error = "That shortcut has no action to run."
            }
        }
    }
    launcherRef = launcher

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tap actions", style = MaterialTheme.typography.titleMedium)
        Text("Choose what happens when you tap each block.", style = MaterialTheme.typography.bodyMedium)
        blocks.forEach { block ->
            val action = actions[block.key] ?: TapAction()
            Row(
                modifier = Modifier.fillMaxWidth().clickable { editing = block.key },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(block.label, style = MaterialTheme.typography.bodyLarge)
                    Text(describe(action), style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = { editing = block.key }) { Text("Change") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    val key = editing
    if (key != null && !activityDialog) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(blocks.firstOrNull { it.key == key }?.label ?: "Tap action") },
            text = {
                Column {
                    Option("Open this app (default)") { onChange(key, TapAction()); editing = null }
                    Option("Do nothing") { onChange(key, TapAction(TapAction.Type.NONE)); editing = null }
                    Option("Open an app…") {
                        error = null; stage = 0
                        launcher.launch(pickerFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
                    }
                    Option("Open an activity…") { activityDialog = true }
                    Option("Run a shortcut…") {
                        error = null; stage = 1
                        launcher.launch(pickerFor(Intent(Intent.ACTION_CREATE_SHORTCUT)))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
    }

    if (key != null && activityDialog) {
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
                        if (component != null) {
                            onChange(key, TapAction.intent(Intent().setComponent(component), component.shortClassName))
                            error = null
                        }
                        activityDialog = false
                        editing = null
                    },
                ) { Text("Use") }
            },
            dismissButton = { TextButton(onClick = { activityDialog = false }) { Text("Back") } },
        )
    }
}

// the launcher lives inside the composition; the shortcut flow needs to start a second screen from its own result
private var launcherRef: androidx.activity.compose.ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>? = null

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}

private fun describe(action: TapAction) = when (action.type) {
    TapAction.Type.DEFAULT -> "Opens this app"
    TapAction.Type.NONE -> "Does nothing"
    TapAction.Type.INTENT -> action.label.ifBlank { "Custom" }
}

private fun labelOf(context: android.content.Context, component: ComponentName): String = try {
    context.packageManager.getActivityInfo(component, 0).loadLabel(context.packageManager).toString()
} catch (_: Throwable) {
    component.shortClassName
}
