package com.captechomar.widgets.widgets

import android.content.Context
import android.content.res.Configuration
import android.os.Build

data class WidgetColors(val container: Int, val track: Int, val fill: Int, val content: Int)

data class PaletteOption(val id: String, val label: String)

object Palettes {
    val options = listOf(
        PaletteOption("dynamic", "Material You"),
        PaletteOption("forest", "Forest"),
        PaletteOption("ocean", "Ocean"),
        PaletteOption("rose", "Rose"),
        PaletteOption("amber", "Amber"),
        PaletteOption("mono", "Mono"),
    )

    private fun isDark(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun resolve(context: Context, id: String): WidgetColors {
        val dark = isDark(context)
        if (id == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return dynamic(context, dark)
        return when (id) {
            "ocean" -> if (dark) WidgetColors(0xFF0A3347.toInt(), 0xFF00202E.toInt(), 0xFF004D6B.toInt(), 0xFF8FD3F4.toInt())
            else WidgetColors(0xFFCFE6F2.toInt(), 0xFFEAF5FB.toInt(), 0xFF8CCBEA.toInt(), 0xFF00354A.toInt())
            "rose" -> if (dark) WidgetColors(0xFF45202D.toInt(), 0xFF2A0F1A.toInt(), 0xFF6F2C42.toInt(), 0xFFFFB1C8.toInt())
            else WidgetColors(0xFFF7D9E1.toInt(), 0xFFFCEEF2.toInt(), 0xFFF3A6BF.toInt(), 0xFF4A1224.toInt())
            "amber" -> if (dark) WidgetColors(0xFF3E2E0A.toInt(), 0xFF241A00.toInt(), 0xFF5E4600.toInt(), 0xFFFFD27A.toInt())
            else WidgetColors(0xFFF5E4BE.toInt(), 0xFFFBF3DF.toInt(), 0xFFF0C766.toInt(), 0xFF3D2A00.toInt())
            "mono" -> if (dark) WidgetColors(0xFF2B2B2B.toInt(), 0xFF121212.toInt(), 0xFF4A4A4A.toInt(), 0xFFE0E0E0.toInt())
            else WidgetColors(0xFFE6E6E6.toInt(), 0xFFF5F5F5.toInt(), 0xFFBDBDBD.toInt(), 0xFF1A1A1A.toInt())
            // "forest" and the fallback for "dynamic" on Android < 12
            else -> if (dark) WidgetColors(0xFF083A31.toInt(), 0xFF001F1A.toInt(), 0xFF005140.toInt(), 0xFF7DDDC0.toInt())
            else WidgetColors(0xFFCDEBE0.toInt(), 0xFFE8F6F0.toInt(), 0xFF8ADBC0.toInt(), 0xFF00382C.toInt())
        }
    }

    private fun dynamic(c: Context, dark: Boolean): WidgetColors =
        if (dark) WidgetColors(
            container = c.getColor(android.R.color.system_accent2_800),
            track = c.getColor(android.R.color.system_accent2_900),
            fill = c.getColor(android.R.color.system_accent1_700),
            content = c.getColor(android.R.color.system_accent1_100),
        ) else WidgetColors(
            container = c.getColor(android.R.color.system_accent2_100),
            track = c.getColor(android.R.color.system_accent2_50),
            fill = c.getColor(android.R.color.system_accent1_300),
            content = c.getColor(android.R.color.system_accent1_900),
        )
}
