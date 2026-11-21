package com.captechomar.widgets.widgets

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.toArgb

/** [accent] / [onAccent] are the tertiary container and its content colour, used for small highlights. */
data class WidgetColors(
    val container: Int, val track: Int, val fill: Int, val content: Int,
    val accent: Int = fill, val onAccent: Int = content,
    /** The battery bar and the text on it; the same as [fill]/[content] unless a palette sets them. */
    val bar: Int = fill, val onBar: Int = content,
    /** The lighter tile on the weather card, and the bright colour for the city name. */
    val tile: Int = container, val accentText: Int = bar,
    /** Text colours of the weather card (onSurface / onSurfaceVariant) and its city name (primaryContainer). */
    val text: Int = content, val textVar: Int = content, val city: Int = accentText,
)

/** Adds tertiary-style accents by turning the hue of the main colours, for palettes that have none of their own. */
fun WidgetColors.withTertiary(): WidgetColors {
    fun turn(c: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[0] = (hsv[0] + 40f) % 360f
        return android.graphics.Color.HSVToColor(android.graphics.Color.alpha(c), hsv)
    }
    return copy(accent = turn(fill), onAccent = turn(content))
}

data class PaletteOption(val id: String, val label: String)

object Palettes {
    /** Material You first, then every palette the Expressive Clock app offers. */
    val options: List<PaletteOption> = listOf(PaletteOption("dynamic", "Wallpaper")) +
        Palette.entries.map { PaletteOption(it.name, it.displayName) }

    // ids saved by earlier versions
    private val legacy = mapOf("forest" to "FOREST", "ocean" to "AZURE", "rose" to "FUCHSIA", "amber" to "CITRON", "mono" to "BASELINE")

    /** Maps ids saved by earlier versions onto the current ones. */
    fun normalize(id: String): String =
        if (id == "dynamic" || Palette.entries.any { it.name == id }) id else legacy[id] ?: "dynamic"

    fun scheme(id: String, dark: Boolean): androidx.compose.material3.ColorScheme {
        val p = Palette.entries.firstOrNull { it.name == id } ?: Palette.entries.firstOrNull { it.name == legacy[id] } ?: Palette.FOREST
        return if (dark) p.dark else p.light
    }

    fun isDark(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun resolve(context: Context, id: String): WidgetColors {
        val dark = isDark(context)
        if (id == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // the weather card's text and city colours come from the same scheme the app itself is drawn with
            val s = if (dark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
            return dynamic(context, dark).copy(text = s.onSurface.toArgb(), textVar = s.onSurfaceVariant.toArgb(), city = s.primaryContainer.toArgb())
        }
        val s = scheme(if (id == "dynamic") "FOREST" else id, dark)
        // Text is drawn straight on the container, exactly like the Expressive Clock's dial, so the
        // pair is surface / onSurface. The bar colour is the first accent that stays readable under that text.
        val content = s.onSurface.toArgb()
        val fill = listOf(s.primaryContainer, s.secondaryContainer, s.tertiaryContainer, s.surfaceContainerHighest)
            .map { it.toArgb() }
            .firstOrNull { androidx.core.graphics.ColorUtils.calculateContrast(content, it) >= 4.5 }
            ?: s.surfaceContainerHighest.toArgb()
        return WidgetColors(
            container = s.surfaceContainerHigh.toArgb(),
            track = s.surfaceContainerLow.toArgb(),
            fill = fill,
            content = content,
            accent = s.tertiaryContainer.toArgb(),
            onAccent = s.onTertiaryContainer.toArgb(),
            bar = s.primary.toArgb(),
            onBar = s.onPrimary.toArgb(),
            tile = s.secondaryContainer.toArgb(),
            accentText = s.primary.toArgb(),
            text = s.onSurface.toArgb(),
            textVar = s.onSurfaceVariant.toArgb(),
            city = s.primaryContainer.toArgb(),
        )
    }

    private fun dynamic(c: Context, dark: Boolean): WidgetColors =
        if (dark) WidgetColors(
            container = c.getColor(android.R.color.system_accent2_800),
            track = c.getColor(android.R.color.system_accent2_900),
            fill = c.getColor(android.R.color.system_accent1_700),
            content = c.getColor(android.R.color.system_accent1_100),
            accent = c.getColor(android.R.color.system_accent3_700),
            onAccent = c.getColor(android.R.color.system_accent3_100),
            tile = c.getColor(android.R.color.system_accent2_700),
            accentText = c.getColor(android.R.color.system_accent1_200),
        ) else WidgetColors(
            container = c.getColor(android.R.color.system_accent2_100),
            track = c.getColor(android.R.color.system_accent2_50),
            fill = c.getColor(android.R.color.system_accent1_300),
            content = c.getColor(android.R.color.system_accent1_900),
            accent = c.getColor(android.R.color.system_accent3_100),
            onAccent = c.getColor(android.R.color.system_accent3_900),
            tile = c.getColor(android.R.color.system_accent2_200),
            accentText = c.getColor(android.R.color.system_accent1_600),
        )
}
