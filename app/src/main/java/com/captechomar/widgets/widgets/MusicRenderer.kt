package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.captechomar.widgets.R
import com.captechomar.widgets.data.WidgetConfig
import com.captechomar.widgets.music.MusicState
import com.captechomar.widgets.music.MusicStore
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the music widget: a play/pause circle, previous, next and the album art.
 * The tap targets in widget_music.xml use the weights 22/16/16/46, matching [CELLS].
 */
object MusicRenderer {
    private val CELLS = floatArrayOf(0.22f, 0.16f, 0.16f, 0.46f)

    fun render(
        context: Context,
        cfg: WidgetConfig,
        w: Int,
        h: Int,
        state: MusicState? = MusicStore.load(context),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val k = 0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)
        val pad = hf * 0.12f

        // background
        val outerR = min(hf / 2f, hf * 0.46f * k)
        paint.color = col.fill
        canvas.drawRoundRect(RectF(0f, 0f, wf, hf), outerR, outerR, paint)

        // play / pause circle
        val cell1 = wf * CELLS[0]
        val d = min(hf - 2 * pad, cell1 * 0.92f)
        val cx = cell1 / 2f
        val cy = hf / 2f
        paint.color = col.content
        canvas.drawCircle(cx, cy, d / 2f, paint)
        val playing = state?.playing == true
        drawIcon(context, canvas, if (playing) R.drawable.ic_pause else R.drawable.ic_play, cx, cy, d * 0.46f, col.fill, 255)

        // previous / next
        val skipSize = min(hf * 0.40f, wf * CELLS[1] * 0.7f)
        drawIcon(context, canvas, R.drawable.ic_skip_previous, wf * (CELLS[0] + CELLS[1] / 2f), cy, skipSize, col.content, 235)
        drawIcon(context, canvas, R.drawable.ic_skip_next, wf * (CELLS[0] + CELLS[1] + CELLS[2] / 2f), cy, skipSize, col.content, 235)

        // album art pill
        val artLeft = wf * (CELLS[0] + CELLS[1] + CELLS[2])
        val artRect = RectF(artLeft, pad, wf - pad, hf - pad)
        val artR = min(artRect.height() / 2f, artRect.height() * 0.5f * k)
        val art = state?.art
        if (art != null) {
            val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = max(artRect.width() / art.width, artRect.height() / art.height)
            val m = Matrix()
            m.setScale(scale, scale)
            m.postTranslate(
                artRect.left + (artRect.width() - art.width * scale) / 2f,
                artRect.top + (artRect.height() - art.height * scale) / 2f,
            )
            shader.setLocalMatrix(m)
            paint.shader = shader
            canvas.drawRoundRect(artRect, artR, artR, paint)
            paint.shader = null
        } else {
            paint.color = col.track
            canvas.drawRoundRect(artRect, artR, artR, paint)
            drawIcon(context, canvas, R.drawable.ic_music_note, artRect.centerX(), artRect.centerY(), artRect.height() * 0.42f, col.content, 200)
        }
        return bmp
    }

    private fun drawIcon(
        context: Context, canvas: Canvas, @DrawableRes res: Int,
        centerX: Float, centerY: Float, size: Float, color: Int, alpha: Int,
    ) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(color)
        d.alpha = alpha
        d.setBounds(
            (centerX - size / 2f).toInt(), (centerY - size / 2f).toInt(),
            (centerX + size / 2f).toInt(), (centerY + size / 2f).toInt(),
        )
        d.draw(canvas)
    }
}
