package com.captechomar.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Builds a widget palette from colors that actually appear in an album cover.
 * Result mapping: fill = background, content = play circle and icons.
 *
 * The cover's dominant color decides the style:
 *  - pastel or earthy and light (sand, sky)      -> that color as a light background, dark buttons
 *  - vivid (red, orange, cyan, royal blue ...)   -> that color as a rich background, pale tinted buttons
 *  - muted or mostly black/white covers          -> a dark background and a vivid accent taken from
 *                                                   elsewhere in the cover (when there is one)
 */
object ArtColors {
    private const val BUCKETS = 12
    private const val SIZE = 48

    private enum class Mode { LIGHT, VIVID, DARK_BG, MUTED }

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
        val hueP = if (main >= 0) hueSum[main] / mainCount else 0f
        val satP = if (main >= 0) satSum[main] / mainCount else 0f
        val valP = if (main >= 0) valSum[main] / mainCount else 0.3f

        val mode = when {
            main < 0 || (darkCount >= total * 0.12f && darkCount >= mainCount * 0.9f) -> Mode.DARK_BG
            valP >= 0.72f && satP <= 0.62f && mainCount >= total * 0.10f -> Mode.LIGHT
            satP >= 0.55f && mainCount >= total * 0.10f -> Mode.VIVID
            else -> Mode.MUTED
        }

        fun hsvColor(h: Float, s: Float, v: Float) =
            Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

        // ---- light, earthy covers: use the color as it is, with dark buttons ----
        if (mode == Mode.LIGHT) {
            val bg = hsvColor(hueP, satP.coerceIn(0.3f, 0.65f), valP.coerceIn(0.8f, 0.92f))
            return WidgetColors(
                container = bg,
                track = hsvColor(hueP, satP.coerceIn(0.3f, 0.65f), 0.72f),
                fill = bg,
                content = hsvColor(hueP, 0.35f, 0.13f),
            )
        }

        // ---- vivid covers: rich background, pale tinted buttons ----
        if (mode == Mode.VIVID) {
            val bgS = satP.coerceIn(0.6f, 0.95f)
            val bgV = valP.coerceIn(0.5f, 0.68f)
            return if (dark) WidgetColors(
                container = hsvColor(hueP, bgS, bgV),
                track = hsvColor(hueP, bgS, bgV * 0.6f),
                fill = hsvColor(hueP, bgS, bgV),
                content = hsvColor(hueP, 0.12f, 0.95f),
            ) else WidgetColors(
                container = hsvColor(hueP, 0.3f, 0.95f),
                track = hsvColor(hueP, 0.2f, 0.97f),
                fill = hsvColor(hueP, 0.3f, 0.93f),
                content = hsvColor(hueP, bgS, 0.55f),
            )
        }

        // ---- dark / muted covers: dark background plus a vivid accent found elsewhere in the cover ----
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
            bgSat = satP.coerceIn(0.3f, 0.55f)
            bgVal = 0.27f
        }

        var accent = -1
        var bestScore = 0f
        for (i in 0 until BUCKETS) {
            if (count[i] < total * 0.015f) continue
            val hue = hueSum[i] / count[i]
            if (mode == Mode.MUTED && hueDistance(hue, bgHue) < 35f) continue
            val score = (satSum[i] / count[i]) * (valSum[i] / count[i]) * sqrt(count[i].toFloat())
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

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 360f - d)
    }
}
