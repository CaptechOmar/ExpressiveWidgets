package com.captechomar.widgets.data

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.captechomar.widgets.widgets.BatteryKind
import com.captechomar.widgets.widgets.WidgetUpdater
import org.json.JSONArray
import org.json.JSONObject

/**
 * Battery levels of devices connected through Gadgetbridge.
 *
 * Gadgetbridge has no API for reading battery levels, but its always-on connection notification shows them.
 * The notification listener (the same access the music widget uses) copies them here.
 */
object GadgetbridgeStore {
    /** Prefix of the pseudo address used to pick a Gadgetbridge device in a widget's settings. */
    const val ADDRESS_PREFIX = "gb:"

    private const val PACKAGE_PREFIX = "nodomain.freeyourgadget.gadgetbridge"
    private const val CHANNEL_ID = "gadgetbridge connection status"
    private const val NOTIFICATION_ID = 1

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("gadgetbridge", Context.MODE_PRIVATE)

    fun addressOf(name: String) = ADDRESS_PREFIX + name

    fun isGbAddress(address: String?) = address?.startsWith(ADDRESS_PREFIX) == true

    fun nameOf(address: String) = address.removePrefix(ADDRESS_PREFIX)

    /** Devices Gadgetbridge currently reports, with their battery level. */
    fun current(context: Context): List<GbDevice> = try {
        val arr = JSONArray(sp(context).getString("current", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            GbDevice(o.getString("name"), o.getInt("percent"))
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** Every device name seen so far, so a device can be picked in the settings while it is switched off. */
    fun known(context: Context): List<String> =
        sp(context).getStringSet("known", emptySet()).orEmpty().sorted()

    fun levelFor(context: Context, name: String): Int =
        current(context).firstOrNull { it.name.equals(name, ignoreCase = true) }?.percent ?: -1

    private fun save(context: Context, devices: List<GbDevice>) {
        val arr = JSONArray()
        devices.forEach { arr.put(JSONObject().put("name", it.name).put("percent", it.percent)) }
        val known = sp(context).getStringSet("known", emptySet()).orEmpty() + devices.map { it.name }
        sp(context).edit()
            .putString("current", arr.toString())
            .putStringSet("known", known)
            .apply()
    }

    // ---- notification handling, called from the notification listener ----

    fun isConnectionNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null || !sbn.packageName.startsWith(PACKAGE_PREFIX)) return false
        return sbn.notification?.channelId == CHANNEL_ID || sbn.id == NOTIFICATION_ID
    }

    private fun parse(sbn: StatusBarNotification): List<GbDevice> {
        val e = sbn.notification.extras
        return GadgetbridgeParser.parse(
            title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        )
    }

    fun onPosted(context: Context, sbn: StatusBarNotification?) {
        if (sbn == null || !isConnectionNotification(sbn)) return
        publish(context, parse(sbn))
    }

    fun onRemoved(context: Context, sbn: StatusBarNotification?) {
        if (sbn == null || !isConnectionNotification(sbn)) return
        publish(context, emptyList())
    }

    /** Re-reads the notification when the listener (re)connects, e.g. after a reboot. */
    fun rescan(context: Context, active: Array<StatusBarNotification>?) {
        val sbn = active.orEmpty().firstOrNull { isConnectionNotification(it) }
        publish(context, sbn?.let { parse(it) }.orEmpty())
    }

    private fun publish(context: Context, devices: List<GbDevice>) {
        val before = current(context)
        save(context, devices)
        if (before != devices) WidgetUpdater.updateKind(context, BatteryKind)
    }
}
