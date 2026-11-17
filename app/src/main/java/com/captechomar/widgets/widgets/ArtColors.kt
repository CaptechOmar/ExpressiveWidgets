package com.captechomar.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/** Builds a widget palette from the dominant color of an album cover. */
object ArtColors {

    fun fromArt(art: Bitmap, dark: Boolean): WidgetColors {
        val small = Bitmap.createScaledBitmap(art, 32, 32, true)
        val weight = FloatArray(12)
        val hueSum = FloatArray(12)
        val satSum = FloatArray(12)
        val hsv = FloatArray(3)
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                Color.colorToHSV(small.getPixel(x, y), hsv)
                val w = hsv[1] * hsv[1] * hsv[2] + 0.0005f
                val b = (hsv[0] / 30f).toInt().coerceIn(0, 11)
                weight[b] += w
                hueSum[b] += hsv[0] * w
                satSum[b] += hsv[1] * w
            }
        }
        var best = 0
        for (i in 1 until 12) if (weight[i] > weight[best]) best = i
        val hue = hueSum[best] / weight[best]
        val sat = satSum[best] / weight[best]

        fun c(s: Float, v: Float) = Color.HSVToColor(floatArrayOf(hue, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        val s = max(min(sat, 0.85f), 0f)
        return if (dark) WidgetColors(
            container = c(s * 0.8f, 0.40f),
            track = c(max(s, 0.3f) * 0.9f, 0.30f),
            fill = c(min(max(s, 0.45f), 0.85f), 0.56f),
            content = c(0.12f, 0.93f),
        ) else WidgetColors(
            container = c(0.2f, 0.95f),
            track = c(0.18f, 0.96f),
            fill = c(min(max(s, 0.3f), 0.55f), 0.86f),
            content = c(min(max(s, 0.4f), 0.8f), 0.25f),
        )
    }
}
