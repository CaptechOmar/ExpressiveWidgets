package com.captechomar.widgets.music

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.captechomar.widgets.data.GadgetbridgeStore
import com.captechomar.widgets.data.OpenScq30Store
import com.captechomar.widgets.widgets.MusicKind
import com.captechomar.widgets.widgets.WidgetUpdater

/**
 * Notification access is what lets an app read other apps' media sessions.
 * While it is enabled the system keeps this service alive, so the widget updates live.
 */
class MediaListenerService : NotificationListenerService() {

    private var manager: MediaSessionManager? = null
    private var tracked: List<MediaController> = emptyList()
    private var currentKey = ""
    private var artKey = ""
    private var clearTimerKey = ""
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        track(controllers.orEmpty())
    }

    // ---- Gadgetbridge: its connection notification carries each device's battery level ----

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        GadgetbridgeStore.onPosted(this, sbn)
        OpenScq30Store.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: NotificationListenerService.RankingMap?,
        reason: Int,
    ) {
        // swiping the notification away or tapping it does not mean the device went away
        if (reason == NotificationListenerService.REASON_CANCEL || reason == NotificationListenerService.REASON_CLICK) return
        GadgetbridgeStore.onRemoved(this, sbn)
        OpenScq30Store.onRemoved(this, sbn)
    }

    override fun onListenerConnected() {
        OpenScq30Store.markListenerConnected(this)
        try {
            GadgetbridgeStore.rescan(this, activeNotifications)
            OpenScq30Store.rescan(this, activeNotifications)
        } catch (_: Throwable) {
        }
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        val component = ComponentName(this, MediaListenerService::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component)
            track(m.getActiveSessions(component))
        } catch (_: SecurityException) {
        }
    }

    override fun onListenerDisconnected() {
        release()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        tracked.forEach { it.unregisterCallback(callback) }
        tracked = emptyList()
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Throwable) {
        }
    }

    private fun track(controllers: List<MediaController>) {
        tracked.forEach { it.unregisterCallback(callback) }
        tracked = controllers
        tracked.forEach { it.registerCallback(callback) }
        refresh()
    }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
    }

    private fun refresh() {
        val controller = tracked.firstOrNull { isPlaying(it) } ?: tracked.firstOrNull() ?: return
        val md = controller.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val pkg = controller.packageName

        val key = "$pkg|$title|$artist"
        currentKey = key
        // Keep showing the previous cover (and its colors) while the new one loads, so the widget
        // doesn't flash back to the default palette. Only drop it if no art shows up within a moment.
        if (artKey != key) {
            val art = loadArt(md)
            if (art != null) {
                MusicStore.saveArt(this, art)
                artKey = key
            } else if (clearTimerKey != key) {
                clearTimerKey = key
                handler.postDelayed({
                    if (currentKey == key && artKey != key) {
                        MusicStore.saveArt(this, null)
                        artKey = key
                        WidgetUpdater.updateKind(this, MusicKind)
                    }
                }, 3000)
            }
        }
        MusicStore.save(this, title, artist, pkg, isPlaying(controller))
        WidgetUpdater.updateKind(this, MusicKind)
    }

    private fun loadArt(md: MediaMetadata): Bitmap? {
        val bitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null) return bitmap
        val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: return null
        return try {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "content" || parsed.scheme == "file") {
                contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (_: Throwable) {
            null
        }
    }
}
