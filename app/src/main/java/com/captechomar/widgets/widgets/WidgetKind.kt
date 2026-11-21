package com.captechomar.widgets.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.captechomar.widgets.R
import com.captechomar.widgets.data.BatteryReader
import com.captechomar.widgets.data.TapAction
import com.captechomar.widgets.data.WeatherStore
import com.captechomar.widgets.data.WidgetConfig
import com.captechomar.widgets.music.MusicStore
import com.captechomar.widgets.ui.MainActivity
import kotlin.math.min

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
    @get:androidx.annotation.DrawableRes val iconRes: Int

    @get:LayoutRes
    val layoutRes: Int
    val usesEarphones: Boolean get() = false
    val needsNotificationAccess: Boolean get() = false
    val supportsArt: Boolean get() = false
    val defaultConfig: WidgetConfig get() = WidgetConfig()

    fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap
    fun bindClicks(context: Context, views: RemoteViews, widgetId: Int, cfg: WidgetConfig, widthDp: Float, heightDp: Float)
}

internal fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context, requestCode, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

object BatteryKind : WidgetKind {
    override val providerClass: Class<*> = BatteryWidgetProvider::class.java
    override val iconRes = R.drawable.kind_battery
    override val title = "Batteries"
    override val description = "Phone battery, plus Bluetooth earphones or a Gadgetbridge device."
    override val layoutRes = R.layout.widget_image
    override val usesEarphones = true

    override fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap =
        BatteryRenderer.render(context, cfg, widthPx, heightPx)

    override fun bindClicks(
        context: Context, views: RemoteViews, widgetId: Int, cfg: WidgetConfig, widthDp: Float, heightDp: Float,
    ) {
        val ids = intArrayOf(R.id.block0, R.id.block1, R.id.block2, R.id.block3, R.id.block4)
        val devices = BatteryReader.devices(context, cfg.deviceAddresses, cfg.manualDevices)
        val keys = listOf(TapAction.PHONE) + devices.map { it.address }
        ids.forEachIndexed { i, id ->
            if (i >= keys.size) {
                views.setViewVisibility(id, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(id, View.VISIBLE)
            val action = cfg.tapActions[keys[i]] ?: TapAction()
            val intent = when (action.type) {
                TapAction.Type.NONE -> null
                TapAction.Type.INTENT -> action.intent()
                TapAction.Type.DEFAULT -> Intent(context, MainActivity::class.java)
            } ?: Intent(context, MainActivity::class.java).takeIf { action.type != TapAction.Type.NONE }
            if (intent == null) {
                views.setOnClickPendingIntent(id, null)
            } else {
                views.setOnClickPendingIntent(
                    id,
                    PendingIntent.getActivity(
                        context, widgetId * 10 + i, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }
    }
}

object MusicKind : WidgetKind {
    override val providerClass: Class<*> = MusicWidgetProvider::class.java
    override val iconRes = R.drawable.kind_music
    override val title = "Music"
    override val description = "Album art with play, previous and next controls for whatever is playing."
    override val layoutRes = R.layout.widget_music
    override val needsNotificationAccess = true
    override val supportsArt = true
    override val defaultConfig = WidgetConfig(roundness = 0.6f, artRoundness = 1f, artColors = true, heightScale = 1f)

    override fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap =
        MusicRenderer.render(context, cfg, widthPx, heightPx)

    override fun bindClicks(
        context: Context, views: RemoteViews, widgetId: Int, cfg: WidgetConfig, widthDp: Float, heightDp: Float,
    ) {
        views.setOnClickPendingIntent(R.id.btn_play, mediaAction(context, MediaActionReceiver.ACTION_PLAY_PAUSE, widgetId))
        views.setOnClickPendingIntent(R.id.btn_prev, mediaAction(context, MediaActionReceiver.ACTION_PREVIOUS, widgetId))
        views.setOnClickPendingIntent(R.id.btn_next, mediaAction(context, MediaActionReceiver.ACTION_NEXT, widgetId))

        // Size the tap targets to the visible bar so the pressed highlight matches it (Android 12+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bar = min(heightDp, widthDp * MusicRenderer.HEIGHT_RATIO * cfg.heightScale.coerceIn(0.6f, 1.6f))
            for (id in intArrayOf(R.id.btn_play, R.id.btn_prev, R.id.btn_next, R.id.btn_art)) {
                views.setViewLayoutHeight(id, bar * 0.8f, TypedValue.COMPLEX_UNIT_DIP)
            }
        }

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
    val all: List<WidgetKind> = listOf(BatteryKind, MusicKind, SavingsKind, WeatherKind)

    fun byProvider(className: String?): WidgetKind? = all.firstOrNull { it.providerClass.name == className }
}

object SavingsKind : WidgetKind {
    override val providerClass: Class<*> = SavingsWidgetProvider::class.java
    override val iconRes = R.drawable.kind_savings
    override val title = "Savings"
    override val description = "A savings goal with progress, monthly change and estimated time."
    override val layoutRes = R.layout.widget_savings
    override val defaultConfig = WidgetConfig(roundness = 1f)

    override fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap =
        SavingsRenderer.render(context, cfg, widthPx, heightPx)

    override fun bindClicks(
        context: Context, views: RemoteViews, widgetId: Int, cfg: WidgetConfig, widthDp: Float, heightDp: Float,
    ) {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        // tapping the card opens its settings
        val settings = Intent(context, com.captechomar.widgets.ui.WidgetConfigActivity::class.java)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, widgetId * 10 + 1, settings, flags))

        // the emoji tile opens the quick calculator
        val d = context.resources.displayMetrics.density
        fun px(dp: Float) = (dp * d).toInt().coerceAtLeast(0)
        val pad = SavingsRenderer.PAD * heightDp
        val tile = SavingsRenderer.TILE * heightDp
        val top = SavingsRenderer.TILE_TOP * heightDp
        views.setViewPadding(
            R.id.emoji_area,
            px(widthDp - pad - tile), px(top), px(pad), px(heightDp - top - tile),
        )
        if (cfg.savings.quickCalc) {
            val adjust = Intent(context, com.captechomar.widgets.ui.SavingsAdjustActivity::class.java)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            views.setOnClickPendingIntent(R.id.btn_emoji, PendingIntent.getActivity(context, widgetId * 10 + 2, adjust, flags))
        } else {
            views.setOnClickPendingIntent(R.id.btn_emoji, PendingIntent.getActivity(context, widgetId * 10 + 1, settings, flags))
        }
    }
}

object WeatherKind : WidgetKind {
    override val providerClass: Class<*> = WeatherWidgetProvider::class.java
    override val iconRes = R.drawable.kind_weather
    override val title = "Weather"
    override val description = "The time and date, current weather, humidity and wind. Uses Open-Meteo."
    override val layoutRes = R.layout.widget_weather
    override val defaultConfig = WidgetConfig(roundness = 1f)

    override fun render(context: Context, cfg: WidgetConfig, widthPx: Int, heightPx: Int): Bitmap =
        WeatherRenderer.render(context, cfg, widthPx, heightPx)

    override fun bindClicks(
        context: Context, views: RemoteViews, widgetId: Int, cfg: WidgetConfig, widthDp: Float, heightDp: Float,
    ) {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val settings = Intent(context, com.captechomar.widgets.ui.WidgetConfigActivity::class.java)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

        // the clock fills the tile drawn in the bitmap
        val f = WeatherRenderer.frame(widthDp, heightDp)
        val tile = f.tile()
        val d = context.resources.displayMetrics.density
        fun px(dp: Float) = (dp * d).toInt().coerceAtLeast(0)
        // tap targets with the pressed highlight, laid over the time, the date and the condition icon
        fun hit(container: Int, button: Int, r: RectF, pi: PendingIntent?) {
            if (pi == null) {
                views.setViewVisibility(container, android.view.View.GONE)
                return
            }
            views.setViewVisibility(container, android.view.View.VISIBLE)
            views.setViewPadding(container, px(r.left), px(r.top), px(widthDp - r.right), px(heightDp - r.bottom))
            views.setOnClickPendingIntent(button, pi)
        }
        fun tapIntent(action: TapAction, fallback: Intent, code: Int): PendingIntent? {
            val intent = when (action.type) {
                TapAction.Type.NONE -> return null
                TapAction.Type.INTENT -> action.intent() ?: fallback
                TapAction.Type.DEFAULT -> fallback
            }
            return PendingIntent.getActivity(context, widgetId * 10 + code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        }
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(cfg.weather.widgetTap, settings, 1))
        val cx = tile.centerX()
        hit(
            R.id.clock_hit, R.id.btn_clock, RectF(cx - 128f * f.k, f.y(58f), cx + 128f * f.k, f.y(170f)),
            tapIntent(cfg.weather.clockTap, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), 2),
        )
        hit(
            R.id.date_hit, R.id.btn_date, RectF(cx - 125f * f.k, f.y(174f), cx + 125f * f.k, f.y(232f)),
            tapIntent(
                cfg.weather.dateTap,
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), 3,
            ),
        )
        val icon = WeatherRenderer.iconBounds(f, WeatherRenderer.tempText(cfg.weather, WeatherStore.cached(context, cfg.weather)))
        icon.inset(-8f * f.k, -8f * f.k)
        val refresh = Intent(context, com.captechomar.widgets.ui.WeatherRefreshActivity::class.java)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        // with all-the-time location the refresh runs in the background; otherwise a screenless page
        // opens for a moment so the app is in the foreground while it reads the location
        val iconTap = if (WeatherStore.hasBackgroundLocation(context)) {
            PendingIntent.getBroadcast(
                context, widgetId * 10 + 5,
                Intent(context, WeatherWidgetProvider::class.java).setAction(WeatherWidgetProvider.ACTION_REFRESH), flags,
            )
        } else PendingIntent.getActivity(context, widgetId * 10 + 4, refresh, flags)
        hit(R.id.icon_hit, R.id.btn_icon, icon, iconTap)

        views.setViewPadding(R.id.clock_area, px(tile.left), px(tile.top), px(widthDp - tile.right), px(heightDp - tile.bottom + 60f * f.k))
        val ink = Palettes.resolve(context, cfg.palette).text
        views.setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_DIP, 98f * f.k)
        views.setTextColor(R.id.clock_time, ink)
    }
}
