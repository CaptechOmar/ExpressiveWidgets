package com.captechomar.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Builds a two-tone widget palette from an album cover: a deep background from the most
 * prominent hue and a vivid accent (play button and icons) from another prominent hue.
 * Result mapping: fill = background, content = accent.
 */
object ArtColors {
    private const val BUCKETS = 12

    fun fromArt(art: Bitmap, dark: Boolean): WidgetColors {
        val small = Bitmap.createScaledBitmap(art, 32, 32, true)
        val weight = FloatArray(BUCKETS)
        val hueSum = FloatArray(BUCKETS)
        val satSum = FloatArray(BUCKETS)
        val valSum = FloatArray(BUCKETS)
        val hsv = FloatArray(3)
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                Color.colorToHSV(small.getPixel(x, y), hsv)
                val w = hsv[1] * hsv[1] * hsv[2] + 0.0005f
                val b = (hsv[0] / 30f).toInt().coerceIn(0, BUCKETS - 1)
                weight[b] += w
                hueSum[b] += hsv[0] * w
                satSum[b] += hsv[1] * w
                valSum[b] += hsv[2] * w
            }
        }
        var main = 0
        for (i in 1 until BUCKETS) if (weight[i] > weight[main]) main = i
        val mainHue = hueSum[main] / weight[main]
        val mainSat = satSum[main] / weight[main]

        // accent: the most vivid other hue that is clearly present and not a neighbour of the main one
        var accent = -1
        var bestScore = 0f
        for (i in 0 until BUCKETS) {
            if (i == main || weight[i] < weight[main] * 0.08f) continue
            val hue = hueSum[i] / weight[i]
            if (hueDistance(hue, mainHue) < 35f) continue
            val score = (satSum[i] / weight[i]) * (valSum[i] / weight[i]) * sqrt(weight[i])
            if (score > bestScore) {
                bestScore = score
                accent = i
            }
        }
        val accHue = if (accent >= 0) hueSum[accent] / weight[accent] else mainHue
        val accSat = if (accent >= 0) satSum[accent] / weight[accent] else mainSat

        fun hsvColor(h: Float, s: Float, v: Float) =
            Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

        // nearly grey covers stay grey instead of being forced into a colour
        val bgSat = if (mainSat < 0.15f) mainSat else mainSat.coerceIn(0.5f, 0.9f)
        val acSat = if (accSat < 0.15f) accSat else accSat.coerceIn(0.55f, 1f)

        return if (dark) WidgetColors(
            container = hsvColor(mainHue, bgSat, 0.50f),
            track = hsvColor(mainHue, bgSat, 0.32f),
            fill = hsvColor(mainHue, bgSat, 0.50f),
            content = hsvColor(accHue, acSat, if (acSat < 0.15f) 0.9f else 0.93f),
        ) else WidgetColors(
            container = hsvColor(mainHue, min(bgSat, 0.25f), 0.95f),
            track = hsvColor(mainHue, min(bgSat, 0.18f), 0.97f),
            fill = hsvColor(mainHue, min(bgSat, 0.25f), 0.93f),
            content = hsvColor(accHue, acSat, if (acSat < 0.15f) 0.3f else 0.62f),
        )
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 360f - d)
    }
}
