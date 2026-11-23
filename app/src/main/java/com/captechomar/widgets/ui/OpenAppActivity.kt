package com.captechomar.widgets.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Opens the app a widget block was set to open. It has no screen. Because it is in the foreground for a moment it may
 * start any activity, and it tries the chosen screen first, then the app's normal launch screen, then any launcher
 * activity of the package, so apps that list a screen but refuse to start it from a widget (Samsung's clock) still open.
 */
class OpenAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)
        var opened = false
        for (candidate in candidates(this, uri)) {
            try {
                startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                opened = true
                break
            } catch (_: Throwable) {
                // try the next way of opening it
            }
        }
        if (!opened) Toast.makeText(this, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val EXTRA_URI = "uri"

        /** An intent that opens [target] through this screen, for a widget's pending intent. */
        fun via(context: Context, target: Intent): Intent =
            Intent(context, OpenAppActivity::class.java)
                .putExtra(EXTRA_URI, target.toUri(0))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // a distinct data URI keeps each block's pending intent separate
                .setData(android.net.Uri.parse("openapp://" + target.toUri(0).hashCode()))

        private fun candidates(context: Context, uri: String?): List<Intent> {
            val primary = try {
                uri?.let { Intent.parseUri(it, 0) }
            } catch (_: Throwable) {
                null
            } ?: return emptyList()
            val pm = context.packageManager
            val pkg = primary.component?.packageName ?: primary.`package`
            val list = mutableListOf(primary)
            if (pkg != null) {
                // the same screen, asked of the app as a whole instead of one named activity
                if (primary.action != null && primary.action != Intent.ACTION_MAIN) {
                    list += Intent(primary.action).setPackage(pkg)
                }
                pm.getLaunchIntentForPackage(pkg)?.let { list += it }
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcher, 0).firstOrNull()?.activityInfo?.let {
                    list += Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(it.packageName, it.name)
                }
            }
            return list
        }
    }
}
