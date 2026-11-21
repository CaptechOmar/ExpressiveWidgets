package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import com.captechomar.widgets.data.DrawingStore
import com.captechomar.widgets.data.Stroke
import com.captechomar.widgets.data.WidgetConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws the drawing widget: a card with the saved strokes on it. The drawing screen uses [drawStrokes] too. */
object DrawingRenderer {

    private val clearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    /**
     * Draws [strokes] with the canvas' top left corner at ([ox], [oy]) and a canvas width of [scale] pixels.
     * They go on their own layer so the eraser only removes ink, never the card behind it.
     */
    fun drawStrokes(canvas: Canvas, strokes: List<Stroke>, ox: Float, oy: Float, scale: Float, ink: Int, bounds: RectF) {
        if (strokes.isEmpty()) return
        val layer = canvas.saveLayer(bounds, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        for (s in strokes) {
            if (s.type == Stroke.CLEAR) {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                continue
            }
            val n = s.pts.size / 2
            if (n == 0) continue
            val erase = s.type == Stroke.ERASE
            val width = (if (erase) DrawingStore.ERASE_W else DrawingStore.INK_W) * scale
            paint.xfermode = if (erase) clearMode else null
            paint.color = if (erase) 0xFF000000.toInt() else ink
            fun x(i: Int) = ox + s.pts[2 * i] * scale
            fun y(i: Int) = oy + s.pts[2 * i + 1] * scale
            if (n == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x(0), y(0), width / 2f, paint)
                continue
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width
            path.reset()
            path.moveTo(x(0), y(0))
            // curve through the midpoints so fast strokes do not look angular
            for (i in 1 until n - 1) path.quadTo(x(i), y(i), (x(i) + x(i + 1)) / 2f, (y(i) + y(i + 1)) / 2f)
            path.lineTo(x(n - 1), y(n - 1))
            canvas.drawPath(path, paint)
        }
        paint.xfermode = null
        canvas.restoreToCount(layer)
    }

    fun render(context: Context, cfg: WidgetConfig, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val W = w.toFloat()
        val H = h.toFloat()
        val k = 0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)
        val r = min(W, H) * 0.14f * k * 1.4f
        val card = RectF(0f, 0f, W, H)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = col.container
        canvas.drawRoundRect(card, r, r, paint)

        val drawing = DrawingStore.load(context, cfg.widgetId)
        if (drawing == null || drawing.strokes.none { it.type != Stroke.CLEAR }) {
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = min(H * 0.16f, W * 0.1f)
            paint.color = col.content
            paint.alpha = 140
            canvas.drawText("Tap to draw", W / 2f, H / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f, paint)
            return bmp
        }
        // the canvas keeps its proportions; if the widget was resized it is centred in the card
        val aspect = drawing.aspect.coerceAtLeast(0.05f)
        val s = min(W, H / aspect)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(card, r, r, Path.Direction.CW) })
        drawStrokes(canvas, drawing.strokes, (W - s) / 2f, (H - s * aspect) / 2f, s, col.content, card)
        canvas.restore()
        return bmp
    }

    fun previewSize(width: Int, aspect: Float) = width to (width * aspect).roundToInt()
}
