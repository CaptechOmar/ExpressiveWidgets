package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.captechomar.widgets.data.ShortcutFiles
import com.captechomar.widgets.data.ShortcutIcons
import com.captechomar.widgets.data.WidgetConfig
import kotlin.math.max
import kotlin.math.min

object ShortcutsRenderer {
    /** What to draw: everything, the card with its tiles, or only the icons (they sit above the tap highlights). */
    enum class Part { ALL, BASE, ICONS }

    /**
     * Where the tiles are, measured from the original widget: the outer tiles are a little wider than the ones
     * between them, and the first gap is a little wider than the rest.
     */
    fun tiles(n: Int, W: Float, H: Float): List<RectF> {
        if (n <= 0) return emptyList()
        val padL = 0.102f * H
        val padR = 0.109f * H
        val padV = 0.105f * H
        val gaps = List(max(n - 1, 0)) { if (it == 0) 0.0884f * H else 0.0748f * H }
        val weights = List(n) { if (it == 0) 1.05f else if (it == n - 1) 1.07f else 1f }
        val unit = (W - padL - padR - gaps.sum()) / weights.sum()
        var x = padL
        return List(n) { i ->
            val r = RectF(x, padV, x + weights[i] * unit, H - padV)
            x = r.right + gaps.getOrElse(i) { 0f }
            r
        }
    }

    fun render(context: Context, cfg: WidgetConfig, w: Int, h: Int, part: Part = Part.ALL): Bitmap {
        val W = max(w, 1).toFloat()
        val H = max(h, 1).toFloat()
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val k = 0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val items = cfg.shortcuts.items
        val rects = tiles(items.size, W, H)

        if (part != Part.ICONS) {
            // the widget itself: rounder on the left than on the right
            val rl = min(H * 0.38f, W / 2f) * k
            val rr = min(H * 0.22f, W / 2f) * k
            paint.color = col.container
            canvas.drawPath(
                Path().apply { addRoundRect(RectF(0f, 0f, W, H), floatArrayOf(rl, rl, rr, rr, rr, rr, rl, rl), Path.Direction.CW) },
                paint,
            )
            rects.forEachIndexed { i, r ->
                val radius = min(r.width(), r.height()) * (if (i == 0) 0.5f else 0.25f) * k
                paint.color = tileColor(col, i, cfg.palette)
                canvas.drawRoundRect(r, radius, radius, paint)
            }
        }
        if (part != Part.BASE) {
            items.forEachIndexed { i, item ->
                drawIcon(context, canvas, item.icon, item.tint, iconColor(col, i, cfg.palette), rects[i])
            }
        }
        return bmp
    }

    

    private fun drawIcon(context: Context, canvas: Canvas, id: String, tint: Boolean, ink: Int, tile: RectF) {
        val size = min(tile.width(), tile.height()) * 0.494f
        val box = RectF(tile.centerX() - size / 2, tile.centerY() - size / 2, tile.centerX() + size / 2, tile.centerY() + size / 2)
        val picture = if (id.startsWith("file:")) ShortcutFiles.bitmap(context, id) else null
        if (picture != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (tint) p.colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN)
            val s = min(box.width() / picture.width, box.height() / picture.height)
            val dw = picture.width * s
            val dh = picture.height * s
            canvas.drawBitmap(picture, null, RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2), p)
            return
        }
        val res = ShortcutIcons.byId(id)?.res ?: return
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(ink)
        d.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
        d.draw(canvas)
    }
}
