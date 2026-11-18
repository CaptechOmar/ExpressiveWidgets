package com.captechomar.widgets.data

data class GbDevice(val name: String, val percent: Int)

/**
 * Reads device names and battery levels out of Gadgetbridge's persistent connection notification.
 *
 * Gadgetbridge builds that notification like this:
 *  - one device:   title = device name, text = "<state>: Battery 80%"
 *  - many devices: title = "N devices connected", big text = one line per device, "<name> (<state>: Battery 80%)"
 *
 * The wording of the state and of "Battery" is translated, so only the "NN%" part is relied on.
 * Plain Kotlin on purpose, so it can be unit tested without Android.
 */
object GadgetbridgeParser {
    private val percent = Regex("""(\d{1,3})\s*%""")
    private val manyDevicesLine = Regex("""^(.+?)\s*\((.*)\)$""")

    /** English state words only. Other languages fall through and the level is shown as reported. */
    private val offline = Regex("""(?i)not connected|disconnected|waiting for reconnect|connecting""")

    fun parse(title: String?, text: String?, bigText: String?): List<GbDevice> {
        val body = bigText?.takeIf { it.isNotBlank() } ?: text.orEmpty()
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val many = ArrayList<GbDevice>()
        for (line in lines) {
            val m = manyDevicesLine.matchEntire(line) ?: continue
            val inside = m.groupValues[2]
            if (offline.containsMatchIn(inside)) continue
            val level = firstPercent(inside) ?: continue
            many += GbDevice(m.groupValues[1].trim(), level)
        }
        if (many.isNotEmpty()) return many

        val name = title?.trim().orEmpty()
        if (name.isEmpty() || offline.containsMatchIn(body)) return emptyList()
        val level = firstPercent(body) ?: return emptyList()
        return listOf(GbDevice(name, level))
    }

    private fun firstPercent(s: String): Int? =
        percent.find(s)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
}
