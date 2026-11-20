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
    // any script's digits, and a percent sign that may be followed by invisible direction marks
    private val percent = Regex("""(\p{Nd}{1,3})[\s\p{Cf}]*[%\u066A\uFF05]""")
    private val connectedTo = Regex("""^(?i)connected to\s+(.+)$""")
    private val offline = Regex("""(?i)awaiting connection|^connecting|disconnected""")

    /** Notification text can carry invisible direction marks (right-to-left locales); they would break every match. */
    private fun clean(s: String?): String =
        s.orEmpty().filter { Character.getType(it) != Character.FORMAT.toInt() }.replace('\u00A0', ' ')

    /** Non-ASCII characters in [s], as code points, for the settings status line. */
    fun oddChars(s: String?): String =
        s.orEmpty().filter { it.code > 127 }.toSet().take(8).joinToString(" ") { "U+%04X".format(it.code) }

    private fun number(digits: String): Int? =
        digits.fold(0) { acc, c -> acc * 10 + Character.digit(c, 10).coerceAtLeast(0) }.takeIf { it in 0..100 }

    fun parse(title: String?, text: String?, bigText: String?): ScqDevice? {
        val head = clean(title).trim()
        if (head.isEmpty() || offline.containsMatchIn(head)) return null
        val name = connectedTo.matchEntire(head)?.groupValues?.get(1)?.trim() ?: head

        val body = clean(bigText?.takeIf { it.isNotBlank() } ?: text)
        var left = -1
        var right = -1
        var case = -1
        var single = -1
        for (line in body.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val level = percent.find(line)?.groupValues?.get(1)?.let { number(it) } ?: continue
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
