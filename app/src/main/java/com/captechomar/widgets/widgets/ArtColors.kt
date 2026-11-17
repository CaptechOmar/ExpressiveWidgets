package com.captechomar.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Builds a widget palette from colors that actually appear in an album cover.
 * Result mapping: fill = background, content = play circle and icons.
 *
 * The cover's dominant color decides the style:
 *  - pastel or earthy and light (sand, sky)           -> that color as a light background, dark buttons
 *  - one vivid hue clearly dominates (red, cyan ...)  -> that color as a rich background, pale tinted buttons
 *  - mixed, muted or mostly black/white covers        -> a dark background and a vivid accent taken from
 *                                                        elsewhere in the cover (when there is one)
 */
object ArtColors {
    private const val BUCKETS = 12
    private const val SIZE = 48

    private enum class Mode { LIGHT, VIVID, DARK_BG, MUTED }

    fun fromArt(art: Bitmap, dark: Boolean): WidgetColors {
        val small = downscale(art, SIZE)
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

        val colored = count.sum()
        var main = -1
        for (i in 0 until BUCKETS) if (count[i] > 0 && (main < 0 || count[i] > count[main])) main = i
        val mainCount = if (main >= 0) count[main] else 0
        val hueP = if (main >= 0) hueSum[main] / mainCount else 0f
        val satP = if (main >= 0) satSum[main] / mainCount else 0f
        val valP = if (main >= 0) valSum[main] / mainCount else 0.3f
        // how much of the colored area belongs to the dominant hue and its two neighbours
        val share = if (main >= 0 && colored > 0) {
            (count[main] + count[(main + 1) % BUCKETS] + count[(main + BUCKETS - 1) % BUCKETS]).toFloat() / colored
        } else 0f

        val mode = when {
            main < 0 || (darkCount >= total * 0.12f && darkCount >= mainCount * 0.9f) -> Mode.DARK_BG
            valP >= 0.72f && satP <= 0.62f && mainCount >= total * 0.10f -> Mode.LIGHT
            satP >= 0.55f && share >= 0.55f && mainCount >= total * 0.10f -> Mode.VIVID
            else -> Mode.MUTED
        }

        fun hsvColor(h: Float, s: Float, v: Float) =
            Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

        // ---- light, earthy covers: use the color as it is, with dark buttons ----
        if (mode == Mode.LIGHT) {
            val s = (satP * 1.1f).coerceIn(0.3f, 0.65f)
            val bg = hsvColor(hueP, s, valP.coerceIn(0.82f, 0.92f))
            return WidgetColors(
                container = bg,
                track = hsvColor(hueP, s, 0.72f),
                fill = bg,
                content = hsvColor(hueP, 0.35f, 0.13f),
            )
        }

        // ---- one vivid hue dominates: rich background, pale tinted buttons ----
        if (mode == Mode.VIVID) {
            // orange covers lean slightly toward red, which reads richer
            val hue = if (hueP < 45f) hueP * 0.7f else hueP
            val bgS = satP.coerceIn(0.65f, 0.97f)
            // equal perceived lightness for every hue, so cyan and red look equally deep
            val bgV = valueForLightness(hue, bgS, 36f).coerceIn(0.4f, 0.8f)
            return if (dark) WidgetColors(
                container = hsvColor(hue, bgS, bgV),
                track = hsvColor(hue, bgS, bgV * 0.6f),
                fill = hsvColor(hue, bgS, bgV),
                content = hsvColor(hue, 0.12f, 0.95f),
            ) else WidgetColors(
                container = hsvColor(hue, 0.3f, 0.95f),
                track = hsvColor(hue, 0.2f, 0.97f),
                fill = hsvColor(hue, 0.3f, 0.93f),
                content = hsvColor(hue, bgS, 0.55f),
            )
        }

        // ---- dark / mixed covers: dark background plus a vivid accent found elsewhere in the cover ----
        val bgHue: Float
        val bgSat: Float
        val bgVal: Float
        if (mode == Mode.DARK_BG && darkCount > 0) {
            Color.colorToHSV(
                Color.rgb((dR / darkCount).toInt(), (dG / darkCount).toInt(), (dB / darkCount).toInt()), hsv,
            )
            bgHue = hsv[0]
            bgSat = hsv[1].coerceIn(0.15f, 0.8f)
            bgVal = (hsv[2] + 0.04f).coerceIn(0.12f, 0.22f)
        } else {
            bgHue = hueP
            bgSat = satP.coerceIn(0.35f, 0.85f)
            bgVal = 0.25f
        }

        var accent = -1
        var bestScore = 0f
        for (i in 0 until BUCKETS) {
            val n = count[i]
            if (n < total * 0.006f) continue
            val sat = satSum[i] / n
            // small areas only count when they are clearly vivid (e.g. a red title on a pale cover)
            if (n < total * 0.015f && sat < 0.5f) continue
            val hue = hueSum[i] / n
            if (mode == Mode.MUTED && hueDistance(hue, bgHue) < 35f) continue
            val score = sat * (valSum[i] / n) * sqrt(n.toFloat())
            if (score > bestScore) {
                bestScore = score
                accent = i
            }
        }
        val accHue = if (accent >= 0) hueSum[accent] / count[accent] else bgHue
        val accSat = if (accent >= 0) (satSum[accent] / count[accent]).coerceIn(0.35f, 0.95f) else 0.15f
        val accVal = if (accent >= 0) (valSum[accent] / count[accent]).coerceIn(0.75f, 0.93f) else 0.9f

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

    /** Halves the bitmap repeatedly so small details (like a title) are averaged instead of skipped. */
    private fun downscale(src: Bitmap, target: Int): Bitmap {
        var bmp = src
        while (bmp.width / 2 >= target && bmp.height / 2 >= target) {
            bmp = Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
        }
        return Bitmap.createScaledBitmap(bmp, target, target, true)
    }

    /** HSV value that gives the requested CIE lightness (L*) for this hue and saturation. */
    private fun valueForLightness(hue: Float, sat: Float, target: Float): Float {
        var lo = 0.15f
        var hi = 1f
        repeat(12) {
            val mid = (lo + hi) / 2f
            if (lightness(Color.HSVToColor(floatArrayOf(hue, sat, mid))) < target) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    private fun lightness(color: Int): Float {
        fun lin(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }
        val y = 0.2126f * lin(Color.red(color)) + 0.7152f * lin(Color.green(color)) + 0.0722f * lin(Color.blue(color))
        return 116f * cbrt(y) - 16f
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 360f - d)
    }
}
