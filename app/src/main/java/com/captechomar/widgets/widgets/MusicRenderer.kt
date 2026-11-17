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

    /** Width-to-height ratio the compact widget keeps, regardless of how tall the launcher cell is. */
    private const val HEIGHT_RATIO = 0.235f

    fun render(
        context: Context,
        cfg: WidgetConfig,
        w: Int,
        h: Int,
        state: MusicState? = MusicStore.load(context),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val art = state?.art
        val col = if (cfg.artColors && art != null) ArtColors.fromArt(art, Palettes.isDark(context))
        else Palettes.resolve(context, cfg.palette)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()

        // The background is a compact bar centred in the space the launcher gives us.
        val ch = min(hf, wf * HEIGHT_RATIO * cfg.heightScale.coerceIn(0.6f, 1.6f))
        val top = (hf - ch) / 2f
        val cy = hf / 2f

        val k = 0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)
        val outerR = min(ch / 2f, ch * 0.46f * k)
        paint.color = col.fill
        canvas.drawRoundRect(RectF(0f, top, wf, top + ch), outerR, outerR, paint)

        // play / pause circle
        val cell1 = wf * CELLS[0]
        val d = min(ch * 0.63f, cell1 * 0.92f)
        val cx = cell1 / 2f
        paint.color = col.content
        canvas.drawCircle(cx, cy, d / 2f, paint)
        val iconSize = d * 0.52f
        val playing = state?.playing == true
        drawIcon(context, canvas, if (playing) R.drawable.ic_pause else R.drawable.ic_play, cx, cy, iconSize, col.fill, 255)

        // previous / next share the play icon's size and style
        drawIcon(context, canvas, R.drawable.ic_skip_previous, wf * (CELLS[0] + CELLS[1] / 2f), cy, iconSize, col.content, 235)
        drawIcon(context, canvas, R.drawable.ic_skip_next, wf * (CELLS[0] + CELLS[1] + CELLS[2] / 2f), cy, iconSize, col.content, 235)

        // album art
        val artLeft = wf * (CELLS[0] + CELLS[1] + CELLS[2])
        val artRect = RectF(artLeft, top + ch * 0.10f, wf - ch * 0.14f, top + ch * 0.90f)
        val ak = 0.2f + 0.8f * cfg.artRoundness.coerceIn(0f, 1f)
        val artR = min(artRect.height() / 2f, artRect.height() * 0.5f * ak)
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
