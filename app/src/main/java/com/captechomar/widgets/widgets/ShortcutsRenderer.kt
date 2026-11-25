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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/** The tile that is being pressed and how far its corners have closed (0 = normal, 1 = fully pressed). */
object ShortcutsAnim {
    class Press(val index: Int, val p: Float)

    private val state = ConcurrentHashMap<Int, Press>()
    fun get(widgetId: Int): Press? = state[widgetId]
    fun set(widgetId: Int, index: Int, p: Float) {
        if (p <= 0f) state.remove(widgetId) else state[widgetId] = Press(index, p)
    }
}

object ShortcutsRenderer {
    /** Layout as fractions of the widget height: space around the tiles and between them. */
    const val PAD = 0.103f
    const val GAP = 0.076f

    fun render(context: Context, cfg: WidgetConfig, w: Int, h: Int): Bitmap {
        val W = max(w, 1).toFloat()
        val H = max(h, 1).toFloat()
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val k = 0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // the widget itself: rounder on the left than on the right
        val rl = min(H * 0.38f, W / 2f) * k
        val rr = min(H * 0.22f, W / 2f) * k
        paint.color = col.container
        canvas.drawPath(
            Path().apply { addRoundRect(RectF(0f, 0f, W, H), floatArrayOf(rl, rl, rr, rr, rr, rr, rl, rl), Path.Direction.CW) },
            paint,
        )

        val items = cfg.shortcuts.items
        val n = items.size
        if (n == 0) return bmp
        val pad = PAD * H
        val gap = GAP * H
        val tw = (W - 2 * pad - (n - 1) * gap) / n
        val th = H - 2 * pad
        val press = ShortcutsAnim.get(cfg.widgetId)
        items.forEachIndexed { i, item ->
            val left = pad + i * (tw + gap)
            val r = RectF(left, pad, left + tw, pad + th)
            var radius = min(tw, th) * (if (i == 0) 0.5f else 0.37f) * k
            if (press != null && press.index == i) radius *= 1f - 0.65f * press.p
            paint.color = if (i == 0) col.accent else col.tile
            canvas.drawRoundRect(r, radius, radius, paint)
            val ink = if (i == 0) col.onAccent else col.onTile
            drawIcon(context, canvas, item.icon, item.tint, ink, r)
        }
        return bmp
    }

    private fun drawIcon(context: Context, canvas: Canvas, id: String, tint: Boolean, ink: Int, tile: RectF) {
        val size = min(tile.width(), tile.height()) * 0.52f
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
