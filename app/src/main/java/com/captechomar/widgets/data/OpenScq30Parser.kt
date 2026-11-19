package com.captechomar.widgets.data

data class ScqDevice(val name: String, val percent: Int)

/**
 * Reads the device name and battery level out of OpenSCQ30's connection notification (Soundcore earbuds):
 *  - title: "Connected to <model>"  (or "Awaiting connection", "Connecting to ...", "Disconnected")
 *  - text:  one "<setting>: <value>" line per setting the user featured, e.g. "Battery Level (Left): 90%"
 *
 * With left and right levels the lower one is used; the case is only used when nothing else is shown.
 * Plain Kotlin so it can be unit tested without Android.
 */
object OpenScq30Parser {
    private val percent = Regex("""(\d{1,3})\s*%""")
    private val connectedTo = Regex("""^(?i)connected to\s+(.+)$""")
    private val offline = Regex("""(?i)awaiting connection|^connecting|disconnected""")

    fun parse(title: String?, text: String?, bigText: String?): ScqDevice? {
        val head = title?.trim().orEmpty()
        if (head.isEmpty() || offline.containsMatchIn(head)) return null
        val name = connectedTo.matchEntire(head)?.groupValues?.get(1)?.trim() ?: head

        val body = bigText?.takeIf { it.isNotBlank() } ?: text.orEmpty()
        val device = ArrayList<Int>()
        val case = ArrayList<Int>()
        for (line in body.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val level = percent.find(line)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 } ?: continue
            if (line.contains("case", ignoreCase = true)) case += level else device += level
        }
        val level = (device.minOrNull() ?: case.minOrNull()) ?: return null
        return ScqDevice(name, level)
    }
}
