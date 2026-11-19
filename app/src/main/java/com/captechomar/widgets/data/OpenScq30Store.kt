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

    /** Which battery of the device a block shows: the whole device, or one earbud, or the case. */
    enum class Part(val suffix: String, val label: String) {
        ALL("", ""), LEFT("|L", " (Left)"), RIGHT("|R", " (Right)"), CASE("|C", " (Case)")
    }

    /** An entry for the settings list. */
    data class Entry(val address: String, val label: String, val part: Part)

    fun addressOf(name: String, part: Part = Part.ALL) = ADDRESS_PREFIX + name + part.suffix
    fun isAddress(address: String?) = address?.startsWith(ADDRESS_PREFIX) == true

    fun split(address: String): Pair<String, Part> {
        val body = address.removePrefix(ADDRESS_PREFIX)
        val part = Part.values().firstOrNull { it != Part.ALL && body.endsWith(it.suffix) } ?: Part.ALL
        return body.removeSuffix(part.suffix) to part
    }

    fun nameOf(address: String) = split(address).first

    /** Name for an address in the settings list, e.g. "Soundcore P30i (Left)". */
    fun labelOf(address: String): String {
        val (name, part) = split(address)
        return name + part.label
    }

    fun current(context: Context): ScqDevice? {
        val name = sp(context).getString("name", null) ?: return null
        val level = sp(context).getInt("percent", -1)
        if (level !in 0..100) return null
        return ScqDevice(
            name, level,
            left = sp(context).getInt("left", -1), right = sp(context).getInt("right", -1),
            case = sp(context).getInt("case", -1), single = sp(context).getInt("single", -1),
        )
    }

    /** The level for one block, or -1 when the device isn't connected or doesn't report that battery. */
    fun levelFor(context: Context, name: String, part: Part = Part.ALL): Int {
        val d = current(context)?.takeIf { it.name == name } ?: return -1
        return when (part) {
            Part.ALL -> d.percent
            Part.LEFT -> d.left
            Part.RIGHT -> d.right
            Part.CASE -> d.case
        }
    }

    /**
     * Everything that can be picked in the settings, also while the device is switched off. A device that reports
     * separate earbuds is offered as left, right (and case) instead of one combined block.
     */
    fun entries(context: Context): List<Entry> {
        val seen = sp(context).getStringSet("known", emptySet()).orEmpty()
        val out = ArrayList<Entry>()
        for (base in seen.filter { !it.contains('|') }.sorted()) {
            val parts = Part.values().filter { it != Part.ALL && (base + it.suffix) in seen }
            if (parts.isEmpty()) out += Entry(addressOf(base), base, Part.ALL)
            else parts.forEach { out += Entry(addressOf(base, it), base + it.label, it) }
        }
        return out
    }

    private fun fromApp(sbn: StatusBarNotification?) = sbn != null && sbn.packageName.startsWith(PACKAGE_PREFIX)

    fun isConnectionNotification(sbn: StatusBarNotification?): Boolean =
        fromApp(sbn) && (sbn!!.notification?.channelId == CHANNEL_ID || sbn.id == NOTIFICATION_ID || sbn.isOngoing)

    private fun parse(sbn: StatusBarNotification): ScqDevice? {
        val e = sbn.notification.extras
        return OpenScq30Parser.parse(
            title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        )
    }

    fun onPosted(context: Context, sbn: StatusBarNotification?) {
        if (sbn == null || !fromApp(sbn)) return
        remember(context, sbn)
        if (!isConnectionNotification(sbn)) return
        publish(context, parse(sbn))
    }

    fun onRemoved(context: Context, sbn: StatusBarNotification?) {
        if (sbn == null || !isConnectionNotification(sbn)) return
        publish(context, null)
    }

    fun rescan(context: Context, active: Array<StatusBarNotification>?) {
        val sbn = active.orEmpty().firstOrNull { isConnectionNotification(it) }
        sbn?.let { remember(context, it) }
        publish(context, sbn?.let { parse(it) })
    }

    /** For the settings screen: what the listener last saw from OpenSCQ30, to help when no level shows up. */
    private fun remember(context: Context, sbn: StatusBarNotification) {
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty().replace('\n', ' ')
        sp(context).edit().putString("last_seen", "$title | $text".take(160)).apply()
    }

    fun markListenerConnected(context: Context) {
        sp(context).edit().putLong("listener_at", System.currentTimeMillis()).apply()
    }

    /** One line describing what was seen, shown in the device settings. */
    fun status(context: Context): String {
        val listener = sp(context).getLong("listener_at", 0L)
        val seen = sp(context).getString("last_seen", null)
        val device = current(context)
        return when {
            device != null -> "OpenSCQ30: reading ${device.name} (${device.percent}%)"
            seen != null -> "OpenSCQ30 notification seen but no battery level found: $seen"
            listener == 0L -> "OpenSCQ30: this app's notification listener hasn't started yet. Turn notification access off and on again for this app."
            else -> "OpenSCQ30: no notification seen yet. Connect your earbuds in OpenSCQ30."
        }
    }

    private fun publish(context: Context, device: ScqDevice?) {
        val before = current(context)
        val e = sp(context).edit()
        if (device == null) e.remove("name").remove("percent").remove("left").remove("right").remove("case").remove("single")
        else {
            val seen = ArrayList<String>().apply {
                add(device.name)
                if (device.left >= 0) add(device.name + Part.LEFT.suffix)
                if (device.right >= 0) add(device.name + Part.RIGHT.suffix)
                if (device.case >= 0) add(device.name + Part.CASE.suffix)
            }
            e.putString("name", device.name).putInt("percent", device.percent)
                .putInt("left", device.left).putInt("right", device.right)
                .putInt("case", device.case).putInt("single", device.single)
                .putStringSet("known", sp(context).getStringSet("known", emptySet()).orEmpty() + seen)
        }
        e.apply()
        if (before != device) WidgetUpdater.updateKind(context, BatteryKind)
    }
}
