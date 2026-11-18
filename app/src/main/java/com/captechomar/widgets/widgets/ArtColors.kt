package com.captechomar.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
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
    private const val SIZE = 96
    private const val FINE = 36

    private enum class Mode { LIGHT, VIVID, DARK_BG, MUTED }

    fun fromArt(art: Bitmap, dark: Boolean): WidgetColors {
        val small = downscale(art, SIZE)
        val total = SIZE * SIZE

        val count = IntArray(BUCKETS)
        val hueSum = FloatArray(BUCKETS)
        val satSum = FloatArray(BUCKETS)
        val valSum = FloatArray(BUCKETS)
        val fCount = IntArray(FINE)
        val fSat = FloatArray(FINE)
        val fVal = FloatArray(FINE)
        val fSin = FloatArray(FINE)
        val fCos = FloatArray(FINE)
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
                if (s >= 0.2f && v >= 0.25f) {
                    val f = (hsv[0] / (360f / FINE)).toInt().coerceIn(0, FINE - 1)
                    val rad = Math.toRadians(hsv[0].toDouble())
                    fCount[f]++
                    fSat[f] += s
                    fVal[f] += v
                    fSin[f] += sin(rad).toFloat()
                    fCos[f] += cos(rad).toFloat()
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

        // share of the colored area that is a clearly different hue from the dominant one
        var otherCount = 0
        for (i in 0 until BUCKETS) if (hueDistance(i * 30f + 15f, hueP) >= 70f) otherCount += count[i]
        val other = if (colored > 0) otherCount.toFloat() / colored else 0f

        val reddish = hueP < 20f || hueP > 330f
        // a cover whose only color is one strong hue (e.g. a red tail light on grey) uses that hue
        val singleHue = main >= 0 && share >= 0.9f && other < 0.05f && satP >= 0.65f && mainCount >= total * 0.035f

        val mode = when {
            singleHue -> Mode.VIVID
            main < 0 || (darkCount >= total * 0.12f && darkCount >= mainCount * 0.9f) -> Mode.DARK_BG
            valP >= 0.72f && satP <= 0.62f && !reddish && mainCount >= total * 0.10f -> Mode.LIGHT
            (satP >= 0.55f || (reddish && satP >= 0.45f)) && share >= 0.55f && other < 0.09f && mainCount >= total * 0.10f -> Mode.VIVID
            else -> Mode.MUTED
        }

        // blue, green, purple covers: a deep tinted background with a vivid accent beats a flat rich background
        val tonal = mode == Mode.VIVID && dark && !reddish && hueP !in 25f..70f

        fun hsvColor(h: Float, s: Float, v: Float) =
            Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

        // ---- light, earthy covers: use the color as it is, with dark buttons ----
        if (mode == Mode.LIGHT) {
            val s = (satP * 1.15f).coerceIn(0.3f, 0.68f)
            val bg = hsvColor(hueP, s, (valP + 0.04f).coerceIn(0.84f, 0.94f))
            return WidgetColors(
                container = bg,
                track = hsvColor(hueP, s, 0.72f),
                fill = bg,
                content = hsvColor(hueP, 0.35f, 0.13f),
            )
        }

        // ---- one vivid hue dominates: rich background, pale tinted buttons ----
        // golden / mustard covers: a dark warm background with the gold as the accent reads better than brown
        if (mode == Mode.VIVID && dark && hueP in 25f..70f) {
            return WidgetColors(
                container = hsvColor(hueP * 0.4f, 0.95f, 0.28f),
                track = hsvColor(hueP * 0.4f, 0.95f, 0.18f),
                fill = hsvColor(hueP * 0.4f, 0.95f, 0.28f),
                content = hsvColor(hueP, 0.55f, 0.85f),
            )
        }

        if (mode == Mode.VIVID && !tonal) {
            // orange covers lean slightly toward red, which reads richer
            val hue = if (hueP < 45f) hueP * 0.7f else hueP
            val bgS = if (reddish) (satP + 0.4f).coerceIn(0.88f, 0.97f) else (satP + 0.12f).coerceIn(0.72f, 0.97f)
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
        var bgHue: Float
        var bgSat: Float
        var bgVal: Float
        // darkest strongly colored area (e.g. a blue shirt in shadow) when the dark pixels themselves are neutral
        var shadow = -1
        for (i in 0 until BUCKETS) {
            if (count[i] < total * 0.03f) continue
            if (satSum[i] / count[i] < 0.5f || valSum[i] / count[i] > 0.45f) continue
            if (shadow < 0 || count[i] > count[shadow]) shadow = i
        }
        if (tonal) {
            val sh = if (shadow >= 0) hueSum[shadow] / count[shadow] else hueP
            bgHue = if (shadow >= 0 && hueDistance(sh, hueP) >= 25f) sh else hueP
            bgSat = satP.coerceIn(0.65f, 0.95f)
            bgVal = 0.22f
        } else if (mode == Mode.DARK_BG && shadow >= 0) {
            bgHue = hueSum[shadow] / count[shadow]
            bgSat = (satSum[shadow] / count[shadow]).coerceIn(0.6f, 0.95f)
            bgVal = 0.24f
        } else if (mode == Mode.DARK_BG && darkCount > 0) {
            Color.colorToHSV(
                Color.rgb((dR / darkCount).toInt(), (dG / darkCount).toInt(), (dB / darkCount).toInt()), hsv,
            )
            bgHue = hsv[0]
            bgSat = hsv[1].coerceIn(0.15f, 0.8f)
            bgVal = (hsv[2] + 0.04f).coerceIn(0.12f, 0.22f)
        } else {
            // pale or greyish blue covers read better as a navy than as a slate
            bgHue = if (hueP in 180f..235f && satP < 0.5f) hueP + (235f - hueP) * 0.6f else hueP
            bgSat = satP.coerceIn(0.35f, 0.85f)
            bgVal = if (hueP in 150f..270f) 0.26f else 0.25f
        }

        // accent: best vivid hue window (30 degrees wide) that differs from the background hue
        // black covers with no strong shadow color: the accent decides the background hue instead
        val neutralDark = mode == Mode.DARK_BG && shadow < 0 && darkCount >= total * 0.5f
        var accHue = bgHue
        var accSat = 0.15f
        var accVal = 0.9f
        var bestScore = 0f
        var bestShare = 0f
        var bestSat = 0f
        // warm yellow / khaki areas are preferred unless a big red or orange area competes with them
        var warmHue = -1f
        var warmSat = 0f
        var warmVal = 0f
        var warmN = 0f
        var khakiN = 0f
        var khakiHue = -1f
        var khakiSat = 0f
        var khakiVal = 0f
        for (i in 0 until FINE) {
            val a = (i + FINE - 1) % FINE
            val b = (i + 1) % FINE
            val n = fCount[a] + fCount[i] + fCount[b]
            if (n < total * 0.0025f) continue
            val sat = (fSat[a] + fSat[i] + fSat[b]) / n
            val vv = (fVal[a] + fVal[i] + fVal[b]) / n
            val hue = ((Math.toDegrees(
                atan2((fSin[a] + fSin[i] + fSin[b]).toDouble(), (fCos[a] + fCos[i] + fCos[b]).toDouble()),
            ).toFloat()) + 360f) % 360f
            val dist = hueDistance(hue, bgHue)
            if (dist < 25f && !neutralDark) continue
            // small areas only count when clearly vivid, or when they are far from the background hue
            if (n < total * 0.015f && sat < 0.5f && !(dist >= 90f && n >= total * 0.007f)) continue
            if (hue in 38f..75f && vv >= 0.65f && n >= total * 0.012f && n > warmN) {
                warmN = n.toFloat()
                warmHue = hue
                warmSat = sat
                warmVal = vv
            }
            if (hue in 50f..75f && sat >= 0.4f && vv >= 0.6f && n > khakiN) {
                khakiN = n.toFloat()
                khakiHue = hue
                khakiSat = sat
                khakiVal = vv
            }
            val redBonus = if ((hue < 20f || hue > 340f) && vv >= 0.6f) 1.5f else 1f
            val score = sat * sat * vv * sqrt(n.toFloat()) * redBonus
            if (score > bestScore) {
                bestScore = score
                bestShare = n.toFloat() / total
                accHue = hue
                accSat = (sat * 1.25f + 0.1f).coerceIn(0.45f, 1f)
                bestSat = sat
                // strongly saturated accents are lifted so they do not look muted on a dark background
                accVal = if (sat >= 0.7f) (vv + 0.12f).coerceIn(0.88f, 0.98f) else vv.coerceIn(0.78f, 0.93f)
            }
        }
        if (warmHue >= 0f && bestShare < 0.08f && bestSat < 0.75f) {
            bestScore = 1f
            accHue = warmHue
            accSat = (warmSat * 1.2f + 0.1f).coerceIn(0.45f, 0.95f)
            accVal = warmVal.coerceIn(0.78f, 0.93f)
        }
        // a big yellow / khaki area (like a painted wall) beats a smaller, different accent
        if (khakiHue >= 0f && khakiN / total >= bestShare && !(warmHue >= 0f && bestShare < 0.08f && bestSat < 0.75f)) {
            bestScore = 1f
            accHue = khakiHue
            accSat = (khakiSat * 1.2f + 0.1f).coerceIn(0.45f, 0.85f)
            accVal = khakiVal.coerceIn(0.78f, 0.93f)
        }
        if (neutralDark && bestScore > 0f && accSat >= 0.7f) {
            bgHue = (accHue + 348f) % 360f
            bgSat = 0.88f
            bgVal = 0.38f
        }
        if (tonal && bestScore == 0f) {
            // single-color cover: lift the background a little and use a pale tint for the buttons
            bgVal = 0.38f
            accHue = bgHue
            accSat = 0.25f
            accVal = 0.9f
        }
        if (bestScore == 0f && !tonal) {
            accSat = 0.15f
            accVal = 0.9f
            accHue = bgHue
        }

        if (dark && mode == Mode.MUTED && reddish && satP >= 0.8f && mainCount >= total * 0.25f && bestScore > 0f) {
            val rich = valueForLightness(bgHue, 0.93f, 27f).coerceIn(0.4f, 0.7f)
            return WidgetColors(
                container = hsvColor(bgHue, 0.93f, rich),
                track = hsvColor(bgHue, 0.93f, rich * 0.6f),
                fill = hsvColor(bgHue, 0.93f, rich),
                content = hsvColor(accHue, 0.3f, 0.6f),
            )
        }

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
