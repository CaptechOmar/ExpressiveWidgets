package com.captechomar.widgets.ui.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** The tools the user starred, saved as "category/tool" keys. */
class Favorites(private val prefs: SharedPreferences) {
    var keys: Set<String> by mutableStateOf(prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet())
        private set

    fun toggle(key: String) {
        keys = if (key in keys) keys - key else keys + key
        prefs.edit().putStringSet(KEY, keys.toSet()).apply()
    }

    private companion object {
        const val KEY = "favorites"
    }
}

@Composable
fun rememberFavorites(): Favorites {
    val context = LocalContext.current.applicationContext
    return remember { Favorites(context.getSharedPreferences("tools", Context.MODE_PRIVATE)) }
}
