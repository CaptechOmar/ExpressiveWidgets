package com.captechomar.widgets.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** An icon picked for a block of the battery widget. */
data class IconChoice(val id: String, val filled: Boolean = false)

/** Per-widget configuration. Add new fields here (with defaults) when new options are needed. */
data class WidgetConfig(
    val palette: String = "dynamic",
    /** Corner roundness of the widget background, 0..1. */
    val roundness: Float = 1f,
    /** Devices to show next to the phone, in order. Empty means automatic: the first connected audio device. */
    val deviceAddresses: List<String> = emptyList(),
    /** Corner roundness of the album art, 0..1 (music widget). */
    val artRoundness: Float = 1f,
    /** Take the widget colors from the album art when there is one (music widget). */
    val artColors: Boolean = true,
    /** Height multiplier for the music widget, 0.6..1.6. */
    val heightScale: Float = 1f,
    /** Tap action per block of the battery widget: [TapAction.PHONE], a device address, or [TapAction.AUTO]. */
    val tapActions: Map<String, TapAction> = emptyMap(),
    /** True once the devices were picked by hand. Until then an empty list means the automatic pick. */
    val manualDevices: Boolean = false,
    /** Every device in the order shown in the settings; [deviceAddresses] is the enabled part of it. */
    val deviceOrder: List<String> = emptyList(),
    /** Icon per block: [TapAction.PHONE] or a device address. */
    val icons: Map<String, IconChoice> = emptyMap(),
)

object WidgetPrefs {
    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

    /** Reads the device list, falling back to the single device that older versions saved. */
    private fun readDevices(sp: SharedPreferences, widgetId: Int): List<String> {
        val json = sp.getString("${widgetId}_devices", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                return (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Throwable) {
            }
        }
        return listOfNotNull(sp.getString("${widgetId}_device", null))
    }

    private fun readActions(sp: SharedPreferences, widgetId: Int): Map<String, TapAction> = try {
        val o = JSONObject(sp.getString("${widgetId}_actions", "{}") ?: "{}")
        o.keys().asSequence().associateWith { TapAction.fromJson(o.getJSONObject(it)) }
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun readList(sp: SharedPreferences, name: String): List<String> = try {
        val arr = JSONArray(sp.getString(name, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun readIcons(sp: SharedPreferences, widgetId: Int): Map<String, IconChoice> = try {
        val o = JSONObject(sp.getString("${widgetId}_icons", "{}") ?: "{}")
        o.keys().asSequence().associateWith {
            val c = o.getJSONObject(it)
            IconChoice(c.getString("id"), c.optBoolean("filled"))
        }
    } catch (_: Throwable) {
        emptyMap()
    }

    fun load(context: Context, widgetId: Int, defaults: WidgetConfig = WidgetConfig()): WidgetConfig {
        val sp = sp(context)
        return WidgetConfig(
            palette = sp.getString("${widgetId}_palette", defaults.palette) ?: defaults.palette,
            roundness = sp.getFloat("${widgetId}_roundness", defaults.roundness),
            deviceAddresses = readDevices(sp, widgetId),
            artRoundness = sp.getFloat("${widgetId}_art_roundness", defaults.artRoundness),
            artColors = sp.getBoolean("${widgetId}_art_colors", defaults.artColors),
            heightScale = sp.getFloat("${widgetId}_height_scale", defaults.heightScale),
            tapActions = readActions(sp, widgetId),
            manualDevices = sp.getBoolean("${widgetId}_manual", false),
            deviceOrder = readList(sp, "${widgetId}_order"),
            icons = readIcons(sp, widgetId),
        )
    }

    fun save(context: Context, widgetId: Int, cfg: WidgetConfig) {
        val e = sp(context).edit()
            .putString("${widgetId}_palette", cfg.palette)
            .putFloat("${widgetId}_roundness", cfg.roundness)
            .putFloat("${widgetId}_art_roundness", cfg.artRoundness)
            .putBoolean("${widgetId}_art_colors", cfg.artColors)
            .putFloat("${widgetId}_height_scale", cfg.heightScale)
        val actions = JSONObject()
        cfg.tapActions.filterValues { it.type != TapAction.Type.DEFAULT }.forEach { (k, v) -> actions.put(k, v.toJson()) }
        e.putString("${widgetId}_actions", actions.toString())
        e.putBoolean("${widgetId}_manual", cfg.manualDevices)
        e.putString("${widgetId}_order", JSONArray(cfg.deviceOrder).toString())
        val icons = JSONObject()
        cfg.icons.forEach { (k, v) -> icons.put(k, JSONObject().put("id", v.id).put("filled", v.filled)) }
        e.putString("${widgetId}_icons", icons.toString())
        e.remove("${widgetId}_device")
        if (cfg.deviceAddresses.isEmpty()) e.remove("${widgetId}_devices")
        else e.putString("${widgetId}_devices", JSONArray(cfg.deviceAddresses).toString())
        e.apply()
    }

    fun delete(context: Context, widgetId: Int) {
        sp(context).edit()
            .remove("${widgetId}_palette")
            .remove("${widgetId}_roundness")
            .remove("${widgetId}_device")
            .remove("${widgetId}_devices")
            .remove("${widgetId}_art_roundness")
            .remove("${widgetId}_art_colors")
            .remove("${widgetId}_height_scale")
            .remove("${widgetId}_actions")
            .remove("${widgetId}_manual")
            .remove("${widgetId}_order")
            .remove("${widgetId}_icons")
            .apply()
    }
}
