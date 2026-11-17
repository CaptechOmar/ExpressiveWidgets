package com.captechomar.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Builds a two-tone widget palette from colors that actually appear in an album cover.
 *
 * Background: the cover's dominant area, darkened. If most of the cover is near-black the
 * background stays near-black (tinted by those pixels). White paper areas are ignored.
 * Accent (play button and icons): a clearly present, vivid color from another part of the cover,
 * or a soft tint of the background when the cover has no second color.
 * Result mapping: fill = background, content = accent.
 */
object ArtColors {
    private const val BUCKETS = 12
    private const val SIZE = 48

    fun fromArt(art: Bitmap, dark: Boolean): WidgetColors {
        val small = Bitmap.createScaledBitmap(art, SIZE, SIZE, true)
        val total = SIZE * SIZE

        val count = IntArray(BUCKETS)
        val hueSum = FloatArray(BUCKETS)
        val satSum = FloatArray(BUCKETS)
        val valSum = FloatArray(BUCKETS)
        var darkCount = 0
        var dR = 0L
        var dG = 0L
        var dB = 0L
        val hsv = FloatArray(3)

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val c = small.getPixel(x, y)
                Color.colorToHSV(c, hsv)
                val s = hsv[1]
                val v = hsv[2]
                if (v < 0.22f) {
                    darkCount++
                    dR += Color.red(c)
                    dG += Color.green(c)
                    dB += Color.blue(c)
                } else if (s >= 0.25f && v >= 0.25f) {
                    val b = (hsv[0] / 30f).toInt().coerceIn(0, BUCKETS - 1)
                    count[b]++
                    hueSum[b] += hsv[0]
                    satSum[b] += s
                    valSum[b] += v
                }
                // greys, whites and skin-like low-saturation areas are ignored on purpose
            }
        }

        var main = -1
        for (i in 0 until BUCKETS) if (count[i] > 0 && (main < 0 || count[i] > count[main])) main = i
        val mainCount = if (main >= 0) count[main] else 0

        val useDark = darkCount >= total * 0.12f && darkCount >= mainCount * 0.9f || main < 0
        val bgHue: Float
        val bgSat: Float
        val bgVal: Float
        if (useDark && darkCount > 0) {
            Color.colorToHSV(
                Color.rgb((dR / darkCount).toInt(), (dG / darkCount).toInt(), (dB / darkCount).toInt()), hsv,
            )
            bgHue = hsv[0]
            bgSat = hsv[1].coerceIn(0.15f, 0.8f)
            bgVal = (hsv[2] + 0.04f).coerceIn(0.12f, 0.22f)
        } else if (main >= 0) {
            bgHue = hueSum[main] / mainCount
            bgSat = (satSum[main] / mainCount).coerceIn(0.35f, 0.85f)
            bgVal = (valSum[main] / mainCount * 0.55f).coerceIn(0.12f, 0.55f)
        } else {
            bgHue = 0f
            bgSat = 0f
            bgVal = 0.2f
        }

        // accent: the most vivid color that is clearly present (and different from a colored background)
        var accent = -1
        var bestScore = 0f
        for (i in 0 until BUCKETS) {
            if (count[i] < total * 0.015f) continue
            val hue = hueSum[i] / count[i]
            if (!useDark && hueDistance(hue, bgHue) < 35f) continue
            val score = (satSum[i] / count[i]) * (valSum[i] / count[i]) * sqrt(count[i].toFloat())
            if (score > bestScore) {
                bestScore = score
                accent = i
            }
        }
        val accHue = if (accent >= 0) hueSum[accent] / count[accent] else bgHue
        val accSat = if (accent >= 0) (satSum[accent] / count[accent]).coerceIn(0.3f, 0.9f) else 0.15f
        val accVal = if (accent >= 0) (valSum[accent] / count[accent]).coerceIn(0.72f, 0.92f) else 0.9f

        fun hsvColor(h: Float, s: Float, v: Float) =
            Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

        return if (dark) WidgetColors(
            container = hsvColor(bgHue, bgSat, bgVal),
            track = hsvColor(bgHue, bgSat, bgVal * 0.65f),
            fill = hsvColor(bgHue, bgSat, bgVal),
            content = hsvColor(accHue, accSat, accVal),
        ) else WidgetColors(
            container = hsvColor(bgHue, min(bgSat, 0.25f), 0.95f),
            track = hsvColor(bgHue, min(bgSat, 0.18f), 0.97f),
            fill = hsvColor(bgHue, min(bgSat, 0.25f), 0.93f),
            content = hsvColor(accHue, accSat, (accVal * 0.7f).coerceIn(0.5f, 0.62f)),
        )
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 360f - d)
    }
}
