package com.captechomar.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import com.captechomar.widgets.data.WidgetPrefs

/**
 * A widget type. To add a new widget (music, weather, savings...):
 *  1. create a renderer + a [WidgetKind] implementation,
 *  2. add an AppWidgetProvider subclass, its appwidget-provider xml and manifest entry,
 *  3. add the kind to [WidgetKinds.all].
 */
interface WidgetKind {
    val providerClass: Class<*>
    val title: String
    fun render(context: Context, widgetId: Int, widthPx: Int, heightPx: Int): Bitmap
}

object BatteryKind : WidgetKind {
    override val providerClass: Class<*> = BatteryWidgetProvider::class.java
    override val title = "Batteries"
    override fun render(context: Context, widgetId: Int, widthPx: Int, heightPx: Int): Bitmap =
        BatteryRenderer.render(context, WidgetPrefs.load(context, widgetId), widthPx, heightPx)
}

object WidgetKinds {
    val all: List<WidgetKind> = listOf(BatteryKind)
}
