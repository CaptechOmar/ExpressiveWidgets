package com.captechomar.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Widget colours taken from a picture the way Material You takes them from a wallpaper: one seed hue from the part
 * of the picture that is actually visible, then fixed light and dark tones of it so text always has strong contrast.
 * The accent pair (emoji tile, pills) is the tertiary hue, 60 degrees round from the seed.
 */
object PictureColors {
    private const val BUCKETS = 36

    fun fromPicture(picture: Bitmap, widthPx: Float, heightPx: Float, dark: Boolean): WidgetColors {
        val ratio = (widthPx / max(heightPx, 1f)).coerceIn(0.5f, 6f)
        var cw = picture.width
        var ch = (cw / ratio).toInt()
        if (ch > picture.height) {
            ch = picture.height
            cw = (ch * ratio).toInt()
        }
        cw = max(cw, 1); ch = max(ch, 1)
        val crop = Bitmap.createBitmap(picture, (picture.width - cw) / 2, (picture.height - ch) / 2, cw, ch)
        val small = Bitmap.createScaledBitmap(crop, 48, max(1, (48 / ratio).toInt()), true)

        val weight = FloatArray(BUCKETS)
        val sinSum = FloatArray(BUCKETS)
        val cosSum = FloatArray(BUCKETS)
        val satSum = FloatArray(BUCKETS)
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            Color.colorToHSV(small.getPixel(x, y), hsv)
            if (hsv[1] < 0.2f || hsv[2] < 0.2f) continue
            val b = ((hsv[0] / 360f) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            val w = hsv[1] * hsv[2]
            weight[b] += w
            val rad = Math.toRadians(hsv[0].toDouble())
            sinSum[b] += (sin(rad) * w).toFloat()
            cosSum[b] += (cos(rad) * w).toFloat()
            satSum[b] += hsv[1] * w
        }
        // the strongest hue, counting its neighbours so one hue split over two buckets still wins
        var best = -1
        var bestScore = 0f
        for (i in 0 until BUCKETS) {
            val score = weight[i] + 0.5f * (weight[(i + 1) % BUCKETS] + weight[(i + BUCKETS - 1) % BUCKETS])
            if (score > bestScore) { bestScore = score; best = i }
        }
        val hue: Float
        val sat: Float
        if (best < 0 || weight[best] <= 0f) {
            hue = 0f
            sat = 0.04f
        } else {
            var h = Math.toDegrees(atan2(sinSum[best].toDouble(), cosSum[best].toDouble())).toFloat()
            if (h < 0) h += 360f
            hue = h
            sat = (satSum[best] / weight[best]).coerceIn(0.3f, 0.75f)
        }
        val hue2 = (hue + 60f) % 360f

        fun hsl(h: Float, s: Float, l: Float) = ColorUtils.HSLToColor(floatArrayOf(h, min(max(s, 0f), 1f), l))
        return if (dark) WidgetColors(
            container = hsl(hue, sat * 0.55f, 0.17f),
            track = hsl(hue, sat * 0.50f, 0.11f),
            fill = hsl(hue, sat * 0.70f, 0.30f),
            content = hsl(hue, min(sat, 0.6f), 0.87f),
            accent = hsl(hue2, sat * 0.60f, 0.27f),
            onAccent = hsl(hue2, min(sat, 0.6f), 0.88f),
        ) else WidgetColors(
            container = hsl(hue, sat * 0.60f, 0.88f),
            track = hsl(hue, sat * 0.50f, 0.94f),
            fill = hsl(hue, sat * 0.70f, 0.72f),
            content = hsl(hue, min(sat, 0.6f), 0.14f),
            accent = hsl(hue2, sat * 0.60f, 0.80f),
            onAccent = hsl(hue2, min(sat, 0.6f), 0.14f),
        )
    }
}
