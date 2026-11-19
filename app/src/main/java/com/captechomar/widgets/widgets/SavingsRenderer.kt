package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.captechomar.widgets.data.SavingsConfig
import com.captechomar.widgets.data.SavingsStore
import com.captechomar.widgets.data.WidgetConfig
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the savings widget into a bitmap. Everything is laid out relative to the widget height,
 * so the card keeps its look when the widget is resized.
 */
object SavingsRenderer {
    // The emoji tile is a tap target too (quick calculator), so the layout shares its position.
    const val PAD = 0.07f
    const val TILE = 0.20f
    const val TILE_TOP = 0.06f

    private val money = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    fun format(currency: String, value: Double) = currency + money.format(value)

    fun render(context: Context, cfg: WidgetConfig, w: Int, h: Int): Bitmap {
        val s = cfg.savings
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val W = w.toFloat()
        val H = h.toFloat()
        val padX = H * PAD
        val k = 0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)
        val outerR = H * 0.14f * k

        // card, with the chosen picture behind a tint so the text stays readable
        val card = RectF(0f, 0f, W, H)
        val bg = SavingsStore.loadBackground(context, s.bgFile)
        if (bg != null) {
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(card, outerR, outerR, Path.Direction.CW) })
            val shader = BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = max(W / bg.width, H / bg.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate((W - bg.width * scale) / 2f, (H - bg.height * scale) / 2f)
            })
            paint.shader = shader
            canvas.drawRect(card, paint)
            paint.shader = null
            paint.color = (col.container and 0x00FFFFFF) or (0xB8 shl 24)
            canvas.drawRect(card, paint)
            canvas.restore()
        } else {
            paint.color = col.container
            canvas.drawRoundRect(card, outerR, outerR, paint)
        }
        val soft = (col.fill and 0x00FFFFFF) or (if (bg != null) 0xCC shl 24 else 0xFF shl 24)

        val now = SavingsConfig.currentMonth()
        val delta = s.monthDelta(now)
        val main = if (s.debtMode) s.remaining else s.saved

        // header: small label, goal name, emoji tile
        val tile = RectF(W - padX - H * TILE, H * TILE_TOP, W - padX, H * TILE_TOP + H * TILE)
        text(canvas, paint, "Savings", padX, H * 0.105f, H * 0.05f, col.content, 0.85f, Paint.Align.LEFT, false, W)
        text(canvas, paint, s.name, padX, H * 0.215f, H * 0.085f, col.content, 1f, Paint.Align.LEFT, true, tile.left - padX - H * 0.04f)
        paint.color = soft
        val tileR = tile.width() * 0.28f * (0.4f + 0.6f * k)
        canvas.drawRoundRect(tile, tileR, tileR, paint)
        if (s.emoji.isNotBlank()) drawEmoji(canvas, s.emoji, tile, s.monoEmoji, col.content)

        // pills: change this month, estimated time
        var px = padX
        val pillTop = H * 0.42f
        val pillH = H * 0.085f
        val pills = ArrayList<String>()
        if (s.monthly) pills += monthText(s, main, delta, now)
        if (s.estimated && delta > 0) {
            if (s.remaining <= 0) pills += "Goal reached"
            else {
                val months = ceil(s.remaining / delta).toInt()
                pills += "~$months ${if (months == 1) "month" else "months"} left"
            }
        }
        for (t in pills) {
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            paint.textSize = H * 0.043f
            val pw = paint.measureText(t) + H * 0.07f
            if (px + pw > W - padX) break
            paint.color = soft
            canvas.drawRoundRect(RectF(px, pillTop, px + pw, pillTop + pillH), pillH / 2f, pillH / 2f, paint)
            text(canvas, paint, t, px + H * 0.035f, pillTop + pillH / 2f, H * 0.043f, col.content, 1f, Paint.Align.LEFT, true, pw)
            px += pw + H * 0.025f
        }

        // main value and target
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = H * 0.062f
        val targetText = format(s.currency, s.target)
        val rightW = max(paint.measureText(targetText), run { paint.textSize = H * 0.042f; paint.measureText("target") })
        text(canvas, paint, "target", W - padX, H * 0.575f, H * 0.042f, col.content, 0.85f, Paint.Align.RIGHT, true, W)
        text(canvas, paint, targetText, W - padX, H * 0.66f, H * 0.062f, col.content, 0.95f, Paint.Align.RIGHT, true, W * 0.4f)
        text(canvas, paint, format(s.currency, main), padX, H * 0.615f, H * 0.14f, col.content, 1f, Paint.Align.LEFT, true, W - 2 * padX - rightW - H * 0.05f)

        // progress
        val barY = H * 0.785f
        val x1End = W - padX
        val barW = x1End - padX
        val frac = if (s.target > 0) (s.saved / s.target).toFloat().coerceIn(0f, 1f) else 0f
        val fillEnd = if (frac > 0f) padX + max(frac * barW, H * 0.09f) else padX
        paint.color = col.content
        if (s.expressive) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            // thin track after the wave
            paint.strokeWidth = H * 0.022f
            paint.color = soft
            canvas.drawLine(min(fillEnd + H * 0.03f, x1End), barY, x1End, barY, paint)
            if (frac > 0f) {
                val path = Path()
                val amp = H * 0.012f
                val lam = H * 0.11f
                path.moveTo(padX, barY)
                var x = padX
                while (x <= fillEnd) {
                    path.lineTo(x, barY + amp * sin(2.0 * PI * (x - padX) / lam).toFloat())
                    x += 1.5f
                }
                paint.strokeWidth = H * 0.04f
                paint.color = col.content
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        } else {
            val t = H * 0.04f
            paint.color = soft
            canvas.drawRoundRect(RectF(padX, barY - t / 2f, x1End, barY + t / 2f), t / 2f, t / 2f, paint)
            if (frac > 0f) {
                paint.color = col.content
                canvas.drawRoundRect(RectF(padX, barY - t / 2f, fillEnd, barY + t / 2f), t / 2f, t / 2f, paint)
            }
        }

        // bottom row
        text(canvas, paint, "${s.percent}%", padX, H * 0.895f, H * 0.056f, col.content, 1f, Paint.Align.LEFT, true, W * 0.3f)
        val right = if (s.debtMode) "paid ${format(s.currency, s.saved)}" else "left ${format(s.currency, s.remaining)}"
        text(canvas, paint, right, W - padX, H * 0.895f, H * 0.05f, col.content, 1f, Paint.Align.RIGHT, true, W * 0.6f)
        return bmp
    }

    private fun monthText(s: SavingsConfig, main: Double, delta: Double, now: String): String {
        val start = if (s.debtMode) max(0.0, s.target - s.monthStart) else s.monthStart
        val change = if (s.monthKey == now) main - start else 0.0
        if (delta == 0.0 || change == 0.0) return "0% this month"
        if (start > 0) {
            val pct = (change / start * 100).roundToInt()
            return "${if (pct > 0) "+" else ""}$pct% this month"
        }
        return "${if (change > 0) "+" else "-"}${format(s.currency, abs(change))} this month"
    }

    /** Draws [str] with its vertical centre at [cy], shrinking it so it fits in [maxWidth]. */
    private fun text(
        canvas: Canvas, paint: Paint, str: String, x: Float, cy: Float, size: Float, color: Int,
        alpha: Float, align: Paint.Align, bold: Boolean, maxWidth: Float,
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create("sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
        paint.textAlign = align
        paint.textSize = size
        val w = paint.measureText(str)
        if (w > maxWidth && maxWidth > 0f) paint.textSize = size * maxWidth / w
        paint.color = color
        paint.alpha = (alpha * 255).roundToInt()
        val fm = paint.fontMetrics
        canvas.drawText(str, x, cy - (fm.ascent + fm.descent) / 2f, paint)
        paint.alpha = 255
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawEmoji(canvas: Canvas, emoji: String, tile: RectF, mono: Boolean, color: Int) {
        val size = tile.width() * 0.56f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        val fm = p.fontMetrics
        if (!mono) {
            canvas.drawText(emoji, tile.centerX(), tile.centerY() - (fm.ascent + fm.descent) / 2f, p)
            return
        }
        // draw the emoji into its own layer, then use only its shape in the text colour
        val side = max(1, (tile.width()).toInt())
        val layer = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(layer).drawText(emoji, side / 2f, side / 2f - (fm.ascent + fm.descent) / 2f, p)
        val tint = Paint(Paint.ANTI_ALIAS_FLAG)
        tint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(layer, tile.left, tile.top, tint)
    }
}
