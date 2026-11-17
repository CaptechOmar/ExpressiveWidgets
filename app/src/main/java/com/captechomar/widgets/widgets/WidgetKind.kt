package com.captechomar.widgets.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.captechomar.widgets.R
import com.captechomar.widgets.data.WidgetConfig
import com.captechomar.widgets.music.MusicStore
import com.captechomar.widgets.ui.MainActivity

/**
 * A widget type. To add a new widget (weather, savings...):
 *  1. write a renderer and a [WidgetKind] implementation,
 *  2. add an AppWidgetProvider subclass of [BaseWidgetProvider], its appwidget-provider xml,
 *     layout and manifest entry,
 *  3. add the kind to [WidgetKinds.all].
 */
interface WidgetKind {
    val providerClass: Class<*>
    val title: String
    val description: String

    @get:LayoutRes
    val layoutRes: Int
    val usesEarphones: Boolean get() = false
    val needsNotificationAccess: Boolean get() = false

    fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap
    fun bindClicks(context: Context, views: RemoteViews, widgetId: Int)
}

internal fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context, requestCode, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

object BatteryKind : WidgetKind {
    override val providerClass: Class<*> = BatteryWidgetProvider::class.java
    override val title = "Batteries"
    override val description = "Phone and Bluetooth earphones battery on your home screen."
    override val layoutRes = R.layout.widget_image
    override val usesEarphones = true

    override fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap =
        BatteryRenderer.render(context, cfg, widthPx, heightPx)

    override fun bindClicks(context: Context, views: RemoteViews, widgetId: Int) {
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, 0))
    }
}

object MusicKind : WidgetKind {
    override val providerClass: Class<*> = MusicWidgetProvider::class.java
    override val title = "Music"
    override val description = "Album art with play, previous and next controls for whatever is playing."
    override val layoutRes = R.layout.widget_music
    override val needsNotificationAccess = true

    override fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap =
        MusicRenderer.render(context, cfg, widthPx, heightPx)

    override fun bindClicks(context: Context, views: RemoteViews, widgetId: Int) {
        views.setOnClickPendingIntent(R.id.btn_play, mediaAction(context, MediaActionReceiver.ACTION_PLAY_PAUSE, widgetId))
        views.setOnClickPendingIntent(R.id.btn_prev, mediaAction(context, MediaActionReceiver.ACTION_PREVIOUS, widgetId))
        views.setOnClickPendingIntent(R.id.btn_next, mediaAction(context, MediaActionReceiver.ACTION_NEXT, widgetId))

        // Art opens the player that is playing, or this app when nothing is known yet.
        val pkg = MusicStore.load(context)?.packageName
        val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        val open = if (launch != null) {
            PendingIntent.getActivity(context, widgetId, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else openAppIntent(context, widgetId)
        views.setOnClickPendingIntent(R.id.btn_art, open)
    }

    private fun mediaAction(context: Context, action: String, widgetId: Int): PendingIntent {
        val intent = Intent(context, MediaActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, widgetId * 10 + action.hashCode().and(0x7), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

object WidgetKinds {
    val all: List<WidgetKind> = listOf(BatteryKind, MusicKind)

    fun byProvider(className: String?): WidgetKind? = all.firstOrNull { it.providerClass.name == className }
}
