package com.captechomar.widgets.data

import android.content.Context

/** Per-widget configuration. Add new fields here (with defaults) when new options are needed. */
data class WidgetConfig(
    val palette: String = "dynamic",
    /** Corner roundness of the widget background, 0..1. */
    val roundness: Float = 1f,
    val deviceAddress: String? = null,
    /** Corner roundness of the album art, 0..1 (music widget). */
    val artRoundness: Float = 1f,
    /** Take the widget colors from the album art when there is one (music widget). */
    val artColors: Boolean = true,
    /** Height multiplier for the music widget, 0.6..1.6. */
    val heightScale: Float = 1f,
)

object WidgetPrefs {
    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

    fun load(context: Context, widgetId: Int, defaults: WidgetConfig = WidgetConfig()): WidgetConfig {
        val sp = sp(context)
        return WidgetConfig(
            palette = sp.getString("${widgetId}_palette", defaults.palette) ?: defaults.palette,
            roundness = sp.getFloat("${widgetId}_roundness", defaults.roundness),
            deviceAddress = sp.getString("${widgetId}_device", null),
            artRoundness = sp.getFloat("${widgetId}_art_roundness", defaults.artRoundness),
            artColors = sp.getBoolean("${widgetId}_art_colors", defaults.artColors),
            heightScale = sp.getFloat("${widgetId}_height_scale", defaults.heightScale),
        )
    }

    fun save(context: Context, widgetId: Int, cfg: WidgetConfig) {
        val e = sp(context).edit()
            .putString("${widgetId}_palette", cfg.palette)
            .putFloat("${widgetId}_roundness", cfg.roundness)
            .putFloat("${widgetId}_art_roundness", cfg.artRoundness)
            .putBoolean("${widgetId}_art_colors", cfg.artColors)
            .putFloat("${widgetId}_height_scale", cfg.heightScale)
        if (cfg.deviceAddress == null) e.remove("${widgetId}_device")
        else e.putString("${widgetId}_device", cfg.deviceAddress)
        e.apply()
    }

    fun delete(context: Context, widgetId: Int) {
        sp(context).edit()
            .remove("${widgetId}_palette")
            .remove("${widgetId}_roundness")
            .remove("${widgetId}_device")
            .remove("${widgetId}_art_roundness")
            .remove("${widgetId}_art_colors")
            .remove("${widgetId}_height_scale")
            .apply()
    }
}
