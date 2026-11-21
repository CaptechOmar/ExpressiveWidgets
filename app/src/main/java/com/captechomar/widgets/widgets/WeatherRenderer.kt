package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.captechomar.widgets.data.Weather
import com.captechomar.widgets.data.WeatherConfig
import com.captechomar.widgets.data.WeatherStore
import com.captechomar.widgets.data.WidgetConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the weather card. Everything is laid out on a 1080 x 328 reference card and scaled to the widget,
 * so the clock tile (a live TextClock laid over the bitmap) can be placed from the same numbers.
 */
object WeatherRenderer {
    const val REF_W = 1080f
    const val REF_H = 328f

    /** The clock tile on the reference card: left, top, right, bottom. */
    val TILE = RectF(85f, 45f, 385f, 283f)

    class Frame(val k: Float, val ox: Float, val oy: Float) {
        fun x(v: Float) = ox + v * k
        fun y(v: Float) = oy + v * k
        fun rect(r: RectF) = RectF(x(r.left), y(r.top), x(r.right), y(r.bottom))
    }

    fun frame(w: Float, h: Float): Frame {
        val k = min(w / REF_W, h / REF_H)
        return Frame(k, (w - REF_W * k) / 2f, (h - REF_H * k) / 2f)
    }

    fun render(context: Context, cfg: WidgetConfig, w: Int, h: Int, weather: Weather? = WeatherStore.cached(context, cfg.weather)): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val W = w.toFloat()
        val H = h.toFloat()
        val f = frame(W, H)
        val k = f.k
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val outerR = H * 0.22f * (0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f))
        paint.color = col.container
        canvas.drawRoundRect(RectF(0f, 0f, W, H), outerR, outerR, paint)

        // clock tile (the time and date themselves are TextClocks on top)
        paint.color = blend(col.container, col.fill, 0.55f)
        val tr = 58f * k * (0.35f + 0.65f * cfg.roundness.coerceIn(0f, 1f))
        canvas.drawRoundRect(f.rect(TILE), tr, tr, paint)

        val wc = cfg.weather
        val imperial = wc.imperial
        fun temp(c: Double) = "${(if (imperial) c * 9 / 5 + 32 else c).roundToInt()}°"

        // current conditions
        text(canvas, paint, "Currently", f.x(560f), f.y(90f), 40f * k, col.content, 0.7f)
        val tempStr = if (weather != null) temp(weather.tempC) else "--°"
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textSize = 76f * k
        val tw = paint.measureText(tempStr)
        val iconD = 64f * k
        val gap = 20f * k
        val startX = f.x(560f) - (tw + gap + iconD) / 2f
        text(canvas, paint, tempStr, startX, f.y(168f), 76f * k, col.content, 1f, Paint.Align.LEFT)
        if (weather != null) {
            drawCondition(canvas, paint, weather.code, weather.isDay, startX + tw + gap + iconD / 2f, f.y(160f), iconD, col.content)
        }
        val place = wc.city.ifBlank { if (weather == null) "Set a city" else "" }
        val tp = TextPaint(paint)
        tp.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        tp.textSize = 36f * k
        val cityMax = 250f * k
        val shown = TextUtils.ellipsize(place, tp, cityMax, TextUtils.TruncateAt.END).toString()
        text(canvas, paint, shown, f.x(560f), f.y(248f), 36f * k, col.bar, 1f)

        // divider
        paint.color = col.content
        paint.alpha = 70
        canvas.drawRoundRect(RectF(f.x(716f), f.y(62f), f.x(718f) + k, f.y(266f)), k, k, paint)
        paint.alpha = 255

        // humidity
        drawDrop(canvas, paint, f.x(790f), f.y(112f), 50f * k, col.content)
        text(canvas, paint, if (weather != null) "${weather.humidity}%" else "--", f.x(836f), f.y(112f), 38f * k, col.content, 1f, Paint.Align.LEFT)

        // wind
        drawWind(canvas, paint, f.x(786f), f.y(236f), 66f * k, col.content)
        val speed = if (weather != null) (if (imperial) weather.windKmh * 0.621371 else weather.windKmh).roundToInt().toString() else "--"
        text(canvas, paint, speed, f.x(872f), f.y(205f), 38f * k, col.content, 1f)
        text(canvas, paint, if (imperial) "mph" else "km/h", f.x(872f), f.y(250f), 34f * k, col.content, 1f)
        drawCompass(canvas, paint, f.x(944f), f.y(222f), 24f * k, weather?.windDir, col.content)
        return bmp
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) * (1 - t) + (b shr s and 0xFF) * t).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun text(
        canvas: Canvas, paint: Paint, str: String, x: Float, cy: Float, size: Float, color: Int,
        alpha: Float, align: Paint.Align = Paint.Align.CENTER,
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textAlign = align
        paint.textSize = size
        paint.color = color
        paint.alpha = (alpha * 255).roundToInt()
        val fm = paint.fontMetrics
        canvas.drawText(str, x, cy - (fm.ascent + fm.descent) / 2f, paint)
        paint.alpha = 255
        paint.textAlign = Paint.Align.LEFT
    }

    // ---- icons, all drawn in a square of side [s] centred on (cx, cy) ----

    private const val SUN = 0xFFFFB300.toInt()
    private const val MOON = 0xFFFFE082.toInt()
    private const val RAIN = 0xFF4FC3F7.toInt()

    private fun drawCondition(canvas: Canvas, paint: Paint, code: Int, day: Boolean, cx: Float, cy: Float, s: Float, ink: Int) {
        paint.style = Paint.Style.FILL
        val cloud = (ink and 0x00FFFFFF) or (0xB0 shl 24)
        when (code) {
            0, 1 -> if (day) sun(canvas, paint, cx, cy, s * 0.5f) else moon(canvas, paint, cx, cy, s * 0.5f)
            2 -> {
                if (day) sun(canvas, paint, cx - s * 0.16f, cy - s * 0.16f, s * 0.38f) else moon(canvas, paint, cx - s * 0.16f, cy - s * 0.16f, s * 0.38f)
                cloud(canvas, paint, cx + s * 0.08f, cy + s * 0.12f, s * 0.78f, cloud)
            }
            3 -> cloud(canvas, paint, cx, cy, s, cloud)
            45, 48 -> {
                paint.color = cloud
                paint.strokeCap = Paint.Cap.ROUND
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.1f
                for (i in -1..1) canvas.drawLine(cx - s * 0.4f + (if (i == 0) s * 0.1f else 0f), cy + i * s * 0.25f, cx + s * 0.4f, cy + i * s * 0.25f, paint)
                paint.style = Paint.Style.FILL
            }
            in 71..77, 85, 86 -> {
                cloud(canvas, paint, cx, cy - s * 0.1f, s * 0.9f, cloud)
                paint.color = 0xFFFFFFFF.toInt()
                for (i in -1..1) canvas.drawCircle(cx + i * s * 0.25f, cy + s * 0.36f, s * 0.05f, paint)
            }
            in 95..99 -> {
                cloud(canvas, paint, cx, cy - s * 0.1f, s * 0.9f, cloud)
                paint.color = SUN
                val p = Path().apply {
                    moveTo(cx + s * 0.04f, cy + s * 0.16f); lineTo(cx - s * 0.12f, cy + s * 0.42f)
                    lineTo(cx - s * 0.01f, cy + s * 0.42f); lineTo(cx - s * 0.06f, cy + s * 0.6f)
                    lineTo(cx + s * 0.14f, cy + s * 0.34f); lineTo(cx + s * 0.03f, cy + s * 0.34f); close()
                }
                canvas.drawPath(p, paint)
            }
            else -> { // drizzle and rain
                cloud(canvas, paint, cx, cy - s * 0.1f, s * 0.9f, cloud)
                paint.color = RAIN
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = s * 0.07f
                for (i in -1..1) canvas.drawLine(cx + i * s * 0.24f + s * 0.04f, cy + s * 0.3f, cx + i * s * 0.24f - s * 0.04f, cy + s * 0.5f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun sun(canvas: Canvas, paint: Paint, cx: Float, cy: Float, d: Float) {
        paint.style = Paint.Style.FILL
        paint.color = SUN
        canvas.drawCircle(cx, cy, d / 2f, paint)
    }

    private fun moon(canvas: Canvas, paint: Paint, cx: Float, cy: Float, d: Float) {
        val a = Path().apply { addCircle(cx, cy, d / 2f, Path.Direction.CW) }
        val b = Path().apply { addCircle(cx + d * 0.28f, cy - d * 0.2f, d * 0.42f, Path.Direction.CW) }
        a.op(b, Path.Op.DIFFERENCE)
        paint.style = Paint.Style.FILL
        paint.color = MOON
        canvas.drawPath(a, paint)
    }

    private fun cloud(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        val base = cy + s * 0.14f
        canvas.drawRoundRect(RectF(cx - s * 0.46f, base - s * 0.16f, cx + s * 0.46f, base + s * 0.16f), s * 0.16f, s * 0.16f, paint)
        canvas.drawCircle(cx - s * 0.16f, base - s * 0.14f, s * 0.2f, paint)
        canvas.drawCircle(cx + s * 0.12f, base - s * 0.2f, s * 0.26f, paint)
    }

    private fun drawDrop(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float, color: Int) {
        val r = s * 0.3f
        val p = Path().apply {
            moveTo(cx, cy - s * 0.5f)
            cubicTo(cx + r * 0.4f, cy - s * 0.2f, cx + r, cy, cx + r, cy + r * 0.45f)
            arcTo(RectF(cx - r, cy + r * 0.45f - r, cx + r, cy + r * 0.45f + r), 0f, 180f, false)
            cubicTo(cx - r, cy, cx - r * 0.4f, cy - s * 0.2f, cx, cy - s * 0.5f)
            close()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.07f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        paint.alpha = 190
        canvas.drawPath(p, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun drawWind(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = s * 0.07f
        paint.color = color
        paint.alpha = 190
        val l = cx - s / 2f
        val r = cx + s / 2f
        canvas.drawLine(l, cy - s * 0.22f, r - s * 0.2f, cy - s * 0.22f, paint)
        canvas.drawLine(l + s * 0.12f, cy, r, cy, paint)
        canvas.drawLine(l, cy + s * 0.22f, r - s * 0.34f, cy + s * 0.22f, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    /** A circled arrow pointing the way the wind blows (wind direction is where it comes from). */
    private fun drawCompass(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, windFrom: Int?, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.14f
        paint.color = color
        paint.alpha = 190
        canvas.drawCircle(cx, cy, r, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.rotate(((windFrom ?: 0) + 180).toFloat(), cx, cy)
        val p = Path().apply {
            moveTo(cx, cy - r * 0.62f); lineTo(cx + r * 0.42f, cy + r * 0.5f)
            lineTo(cx, cy + r * 0.2f); lineTo(cx - r * 0.42f, cy + r * 0.5f); close()
        }
        canvas.drawPath(p, paint)
        canvas.restore()
    }
}
