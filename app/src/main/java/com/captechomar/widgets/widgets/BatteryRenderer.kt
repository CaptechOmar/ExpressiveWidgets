package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.captechomar.widgets.R
import com.captechomar.widgets.data.BatteryReader
import com.captechomar.widgets.data.BtBattery
import com.captechomar.widgets.data.GadgetbridgeStore
import com.captechomar.widgets.data.PhoneBattery
import com.captechomar.widgets.data.WidgetConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the battery widget into a bitmap: the phone on the left and, when one is connected, a second
 * device (earphones, or a Gadgetbridge device) on the right. With nothing connected, the phone bar
 * fills the whole widget.
 */
object BatteryRenderer {

    // sizes relative to the bar height
    private const val ICON_SIZE = 0.30f
    private const val TEXT_SIZE = 0.23f
    private const val EDGE_MARGIN = 0.18f

    fun render(
        context: Context,
        cfg: WidgetConfig,
        w: Int,
        h: Int,
        phone: PhoneBattery = BatteryReader.phone(context),
        buds: BtBattery? = BatteryReader.earphones(context, cfg.deviceAddress),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val col = Palettes.resolve(context, cfg.palette)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val k = 0.2f + 0.8f * cfg.roundness.coerceIn(0f, 1f)
        val pad = h * 0.10f
        val gap = h * 0.07f
        val trackH = h - 2 * pad
        val trackW = if (buds != null) (w - 2 * pad - gap) / 2f else w - 2 * pad
        val trackR = trackH * 0.5f * k
        val outerR = min(trackR + pad, h / 2f)

        paint.color = col.container
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), outerR, outerR, paint)

        drawBar(
            context, canvas, paint, RectF(pad, pad, pad + trackW, pad + trackH), trackR, col,
            percent = phone.percent, icon = R.drawable.ic_phone, charging = phone.charging,
        )
        if (buds != null) {
            val icon = when {
                GadgetbridgeStore.isGbAddress(buds.address) -> R.drawable.ic_watch
                else -> R.drawable.ic_headphones
            }
            drawBar(
                context, canvas, paint, RectF(w - pad - trackW, pad, w - pad, pad + trackH), trackR, col,
                percent = buds.percent, icon = icon, charging = false,
            )
        }
        return bmp
    }

    private fun drawBar(
        context: Context,
        canvas: Canvas,
        paint: Paint,
        track: RectF,
        trackR: Float,
        col: WidgetColors,
        percent: Int,
        @DrawableRes icon: Int,
        charging: Boolean,
    ) {
        val trackH = track.height()
        paint.style = Paint.Style.FILL
        paint.color = col.track
        canvas.drawRoundRect(track, trackR, trackR, paint)

        if (percent >= 0) {
            val minFill = min(trackH * 0.38f, track.width())
            val fillW = max(percent.coerceIn(0, 100) / 100f * track.width(), minFill)
            val fillR = min(trackR, min(fillW / 2f, trackH / 2f))
            paint.color = col.fill
            canvas.drawRoundRect(RectF(track.left, track.top, track.left + fillW, track.bottom), fillR, fillR, paint)
        }

        val iconSize = trackH * ICON_SIZE
        val cy = track.centerY()
        drawIcon(context, canvas, icon, track.left + trackH * EDGE_MARGIN, cy, iconSize, col.content)

        paint.color = col.content
        paint.textSize = trackH * TEXT_SIZE
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT
        val text = if (percent >= 0) "$percent%" else "--"
        val fm = paint.fontMetrics
        val textRight = track.right - trackH * EDGE_MARGIN
        canvas.drawText(text, textRight, cy - (fm.ascent + fm.descent) / 2f, paint)

        if (charging) {
            val textLeft = textRight - paint.measureText(text)
            val boltSize = iconSize * 0.85f
            drawIcon(context, canvas, R.drawable.ic_bolt, textLeft - boltSize - trackH * 0.04f, cy, boltSize, col.content)
        }
    }

    private fun drawIcon(
        context: Context, canvas: Canvas, @DrawableRes res: Int,
        left: Float, centerY: Float, size: Float, color: Int,
    ) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(color)
        d.setBounds(left.toInt(), (centerY - size / 2f).toInt(), (left + size).toInt(), (centerY + size / 2f).toInt())
        d.draw(canvas)
    }
}
