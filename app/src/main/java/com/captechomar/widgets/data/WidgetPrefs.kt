package com.captechomar.widgets.data

import android.content.Context

/** Per-widget configuration. Add new fields here (with defaults) when new options are needed. */
data class WidgetConfig(
    val palette: String = "dynamic",
    val roundness: Float = 1f,
    val deviceAddress: String? = null,
)

object WidgetPrefs {
    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

    fun load(context: Context, widgetId: Int): WidgetConfig {
        val sp = sp(context)
        val d = WidgetConfig()
        return WidgetConfig(
            palette = sp.getString("${widgetId}_palette", d.palette) ?: d.palette,
            roundness = sp.getFloat("${widgetId}_roundness", d.roundness),
            deviceAddress = sp.getString("${widgetId}_device", null),
        )
    }

    fun save(context: Context, widgetId: Int, cfg: WidgetConfig) {
        sp(context).edit()
            .putString("${widgetId}_palette", cfg.palette)
            .putFloat("${widgetId}_roundness", cfg.roundness)
            .apply {
                if (cfg.deviceAddress == null) remove("${widgetId}_device")
                else putString("${widgetId}_device", cfg.deviceAddress)
            }
            .apply()
    }

    fun delete(context: Context, widgetId: Int) {
        sp(context).edit()
            .remove("${widgetId}_palette")
            .remove("${widgetId}_roundness")
            .remove("${widgetId}_device")
            .apply()
    }
}
