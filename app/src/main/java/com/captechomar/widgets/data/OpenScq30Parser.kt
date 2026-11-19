package com.captechomar.widgets.data

/** [percent] is the combined level (the lower of left and right, or the single battery); -1 means "not shown". */
data class ScqDevice(
    val name: String, val percent: Int,
    val left: Int = -1, val right: Int = -1, val case: Int = -1, val single: Int = -1,
)

/**
 * Reads the device name and battery level out of OpenSCQ30's connection notification (Soundcore earbuds):
 *  - title: "Connected to <model>"  (or "Awaiting connection", "Connecting to ...", "Disconnected")
 *  - text:  one "<setting>: <value>" line per setting the user featured, e.g. "Battery Level (Left): 90%"
 *
 * Left, right and case levels are kept apart so each can be its own block; the combined level is the lower of left and right.
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
        var left = -1
        var right = -1
        var case = -1
        var single = -1
        for (line in body.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val level = percent.find(line)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 } ?: continue
            when {
                line.contains("case", ignoreCase = true) -> case = level
                line.contains("left", ignoreCase = true) -> left = level
                line.contains("right", ignoreCase = true) -> right = level
                else -> single = if (single < 0) level else minOf(single, level)
            }
        }
        val parts = listOf(left, right, single).filter { it >= 0 }
        val level = parts.minOrNull() ?: case.takeIf { it >= 0 } ?: return null
        return ScqDevice(name, level, left, right, case, single)
    }
}
