package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.captechomar.widgets.R
import com.captechomar.widgets.data.Weather
import com.captechomar.widgets.data.WeatherStore
import com.captechomar.widgets.data.WidgetConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the weather card. Everything is laid out on a 960 x 284 reference card. Sizes scale with the widget's
 * height; on a wider widget the spare width goes into the gaps between the three groups (clock, weather, humidity
 * and wind) so nothing gets squeezed. The clock tile (a live TextClock laid over the bitmap) is placed from the same numbers.
 */
object WeatherRenderer {
    const val REF_W = 960f
    const val REF_H = 284f

    /** The clock tile on the full reference card: left, top, right, bottom. */
    val TILE = RectF(22f, 23f, 316f, 261f)

    /** On a narrow widget the humidity and wind column is dropped and the tile grows. */
    private val TILE_COMPACT = RectF(22f, 23f, 387f, 261f)
    private const val COMPACT_W = 794f
    private const val COMPACT_BELOW_ASPECT = 3.1f

    class Frame(val k: Float, val oy: Float, private val spare: Float, val compact: Boolean) {
        /** Left-aligned group (the tile). */
        fun x(v: Float) = v * k
        /** The "Currently" group, pushed right by part of the spare width. */
        fun xm(v: Float) = v * k + spare * (if (compact) 0.5f else 0.4f)
        /** The divider and the humidity / wind group. */
        fun xr(v: Float) = v * k + spare * 0.85f
        fun y(v: Float) = oy + v * k
        fun rect(r: RectF) = RectF(x(r.left), y(r.top), x(r.right), y(r.bottom))
        fun tile() = rect(if (compact) TILE_COMPACT else TILE)
    }

    fun frame(w: Float, h: Float): Frame {
        val compact = w / h < COMPACT_BELOW_ASPECT
        val refW = if (compact) COMPACT_W else REF_W
        val k = min(w / refW, h / REF_H)
        return Frame(k, (h - REF_H * k) / 2f, max(0f, w - refW * k), compact)
    }

    /** "Sat 21st Nov"; the ordinal suffix is English only. */
    private fun dateText(): String {
        val now = java.util.Calendar.getInstance()
        val loc = java.util.Locale.getDefault()
        val day = now.get(java.util.Calendar.DAY_OF_MONTH)
        val suffix = if (loc.language != "en") "" else when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        val d = now.time
        return "${java.text.SimpleDateFormat("EEE", loc).format(d)} $day$suffix ${java.text.SimpleDateFormat("MMM", loc).format(d)}"
    }

    private val bitmaps = HashMap<Int, Bitmap>()

    /** Weather Now icons (Grabster, CC BY-SA 4.0). Index 1 is the day variant and 0 the night one. */
    private fun iconFor(code: Int, day: Boolean): Int {
        val d = if (day) 1 else 0
        fun pick(day1: Int, night0: Int) = if (d == 1) day1 else night0
        return when (code) {
            0, 1 -> pick(R.drawable.wn_clear1, R.drawable.wn_clear0)
            2 -> pick(R.drawable.wn_pcloudy1, R.drawable.wn_pcloudy0)
            3 -> pick(R.drawable.wn_mcloudy1, R.drawable.wn_mcloudy0)
            45, 48 -> pick(R.drawable.wn_fog1, R.drawable.wn_fog0)
            51, 53, 55, 80, 81 -> pick(R.drawable.wn_shower1, R.drawable.wn_shower0)
            56, 57, 66, 67 -> pick(R.drawable.wn_sleet1, R.drawable.wn_sleet0)
            61, 63, 65, 82 -> pick(R.drawable.wn_rain1, R.drawable.wn_rain0)
            71, 85 -> pick(R.drawable.wn_lsnow1, R.drawable.wn_lsnow0)
            73, 75, 77, 86 -> pick(R.drawable.wn_snow1, R.drawable.wn_snow0)
            95 -> pick(R.drawable.wn_tstorm1, R.drawable.wn_tstorm0)
            96, 99 -> pick(R.drawable.wn_tshower1, R.drawable.wn_tshower0)
            else -> pick(R.drawable.wn_unknown1, R.drawable.wn_unknown0)
        }
    }

    private fun bitmap(context: Context, res: Int): Bitmap =
        bitmaps.getOrPut(res) { BitmapFactory.decodeResource(context.resources, res) }

    fun render(context: Context, cfg: WidgetConfig, w: Int, h: Int, weather: Weather? = WeatherStore.cached(context, cfg.weather)): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val W = w.toFloat()
        val H = h.toFloat()
        val f = frame(W, H)
        val k = f.k
        val round = cfg.roundness.coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // dark card with a lighter clock tile on it
        val outerR = H * 0.3f * (0.25f + 0.75f * round)
        paint.color = col.track
        canvas.drawRoundRect(RectF(0f, 0f, W, H), outerR, outerR, paint)
        paint.color = col.tile
        val tr = 64f * k * (0.6f + 0.4f * round)
        val tile = f.tile()
        canvas.drawRoundRect(tile, tr, tr, paint)
        text(canvas, paint, dateText(), tile.centerX(), f.y(201f), 37.5f * k, col.onTile, 1f)

        val wc = cfg.weather
        val imperial = wc.imperial
        fun temp(c: Double) = "${(if (imperial) c * 9 / 5 + 32 else c).roundToInt()}°"

        // current conditions
        val mx = f.xm(if (f.compact) 590f else 494f)
        text(canvas, paint, "Currently", mx, f.y(66f), 42f * k, col.textVar, 1f)
        val tempStr = if (weather != null) temp(weather.tempC) else "--°"
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textSize = 68f * k
        val tw = paint.measureText(tempStr)
        val iconD = 76f * k
        val gap = 12f * k
        val startX = mx - (tw + gap + iconD) / 2f
        text(canvas, paint, tempStr, startX, f.y(140f), 68f * k, col.text, 1f, Paint.Align.LEFT)
        if (weather != null) {
            val src = bitmap(context, iconFor(weather.code, weather.isDay))
            val cx = startX + tw + gap + iconD / 2f
            val cy = f.y(138f)
            canvas.drawBitmap(src, null, RectF(cx - iconD / 2f, cy - iconD / 2f, cx + iconD / 2f, cy + iconD / 2f), paint)
        }
        val place = wc.city.ifBlank { if (weather == null) "Set a city" else "" }
        val tp = TextPaint(paint)
        tp.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        tp.textSize = 40f * k
        val shown = TextUtils.ellipsize(place, tp, 262f * k, TextUtils.TruncateAt.END).toString()
        text(canvas, paint, shown, mx, f.y(218f), 40f * k, col.city, 1f)

        if (f.compact) return bmp

        // divider
        val dx = f.xr(651f)
        paint.color = col.city
        paint.alpha = 255
        canvas.drawRoundRect(RectF(dx, f.y(38f), dx + 2f * k + 1f, f.y(246f)), k, k, paint)
        paint.alpha = 255

        // humidity
        icon(context, canvas, R.drawable.wx_drop, f.xr(719f), f.y(90f), 76f * k, col.textVar, 1f)
        text(canvas, paint, if (weather != null) "${weather.humidity}%" else "--", f.xr(770f), f.y(90f), 38f * k, col.text, 1f, Paint.Align.LEFT)

        // wind
        icon(context, canvas, R.drawable.wx_wind, f.xr(717f), f.y(206f), 76f * k, col.textVar, 1f)
        val speed = if (weather != null) (if (imperial) weather.windKmh * 0.621371 else weather.windKmh).roundToInt().toString() else "--"
        text(canvas, paint, speed, f.xr(804f), f.y(185f), 40f * k, col.text, 1f)
        text(canvas, paint, if (imperial) "mph" else "km/h", f.xr(807f), f.y(234f), 34f * k, col.text, 1f)
        compass(context, canvas, paint, f.xr(874f), f.y(206f), 22f * k, weather?.windDir, col.text)
        return bmp
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

    /** A Material Symbol drawn tinted in a square of side [s] centred on (cx, cy). */
    private fun icon(context: Context, canvas: Canvas, res: Int, cx: Float, cy: Float, s: Float, color: Int, alpha: Float, rotate: Float = 0f) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(color)
        d.alpha = (alpha * 255).roundToInt()
        d.setBounds((cx - s / 2f).roundToInt(), (cy - s / 2f).roundToInt(), (cx + s / 2f).roundToInt(), (cy + s / 2f).roundToInt())
        canvas.save()
        canvas.rotate(rotate, cx, cy)
        d.draw(canvas)
        canvas.restore()
    }

    /** A circled arrow pointing the way the wind blows (wind direction is where it comes from). */
    private fun compass(context: Context, canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, windFrom: Int?, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.12f
        paint.color = color
        paint.alpha = 204
        canvas.drawCircle(cx, cy, r, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        icon(context, canvas, R.drawable.wx_arrow, cx, cy, r * 1.35f, color, 1f, ((windFrom ?: 0) + 180).toFloat())
    }
}
