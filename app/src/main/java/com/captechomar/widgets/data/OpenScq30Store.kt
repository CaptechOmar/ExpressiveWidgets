package com.captechomar.widgets.data

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.captechomar.widgets.widgets.BatteryKind
import com.captechomar.widgets.widgets.WidgetUpdater

/**
 * Battery level of a Soundcore device connected through OpenSCQ30, read from its connection notification
 * by the notification listener (the same access the music widget and Gadgetbridge support use).
 * The battery setting has to be shown in OpenSCQ30's status notification for a level to appear.
 */
object OpenScq30Store {
    const val ADDRESS_PREFIX = "scq:"

    private const val PACKAGE_PREFIX = "com.oppzippy.openscq30"
    private const val CHANNEL_ID = "com.oppzippy.openscq30.notification.DeviceServiceChannel"
    private const val NOTIFICATION_ID = 1

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("openscq30", Context.MODE_PRIVATE)

    fun addressOf(name: String) = ADDRESS_PREFIX + name
    fun isAddress(address: String?) = address?.startsWith(ADDRESS_PREFIX) == true
    fun nameOf(address: String) = address.removePrefix(ADDRESS_PREFIX)

    fun current(context: Context): ScqDevice? {
        val name = sp(context).getString("name", null) ?: return null
        val level = sp(context).getInt("percent", -1)
        return if (level in 0..100) ScqDevice(name, level) else null
    }

    /** Every device name seen so far, so it can be picked in the settings while disconnected. */
    fun known(context: Context): List<String> =
        sp(context).getStringSet("known", emptySet()).orEmpty().sorted()

    fun levelFor(context: Context, name: String): Int =
        current(context)?.takeIf { it.name == name }?.percent ?: -1

    fun isConnectionNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null || !sbn.packageName.startsWith(PACKAGE_PREFIX)) return false
        return sbn.notification?.channelId == CHANNEL_ID || sbn.id == NOTIFICATION_ID
    }

    private fun parse(sbn: StatusBarNotification): ScqDevice? {
        val e = sbn.notification.extras
        return OpenScq30Parser.parse(
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
        publish(context, null)
    }

    fun rescan(context: Context, active: Array<StatusBarNotification>?) {
        val sbn = active.orEmpty().firstOrNull { isConnectionNotification(it) }
        publish(context, sbn?.let { parse(it) })
    }

    private fun publish(context: Context, device: ScqDevice?) {
        val before = current(context)
        val e = sp(context).edit()
        if (device == null) e.remove("name").remove("percent")
        else {
            e.putString("name", device.name).putInt("percent", device.percent)
                .putStringSet("known", sp(context).getStringSet("known", emptySet()).orEmpty() + device.name)
        }
        e.apply()
        if (before != device) WidgetUpdater.updateKind(context, BatteryKind)
    }
}
