package com.captechomar.widgets.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

/** Sends a media key to whichever player is active, the same as a headset button. */
class MediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = when (intent.action) {
            ACTION_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            ACTION_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.captechomar.widgets.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.captechomar.widgets.action.NEXT"
        const val ACTION_PREVIOUS = "com.captechomar.widgets.action.PREVIOUS"
    }
}
