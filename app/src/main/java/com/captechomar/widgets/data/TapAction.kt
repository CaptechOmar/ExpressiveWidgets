package com.captechomar.widgets.data

import android.content.Intent
import org.json.JSONObject

/** What happens when a block of a widget is tapped. */
data class TapAction(val type: Type = Type.DEFAULT, val uri: String = "", val label: String = "") {
    enum class Type { DEFAULT, NONE, INTENT }

    /** The intent to start for [Type.INTENT] (an app, an activity or a shortcut), or null if it can't be built. */
    fun intent(): Intent? = try {
        if (type == Type.INTENT) Intent.parseUri(uri, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else null
    } catch (_: Throwable) {
        null
    }

    fun toJson(): JSONObject = JSONObject().put("type", type.name).put("uri", uri).put("label", label)

    companion object {
        const val PHONE = "phone"
        const val AUTO = "auto"

        fun fromJson(o: JSONObject) = TapAction(
            type = runCatching { Type.valueOf(o.getString("type")) }.getOrDefault(Type.DEFAULT),
            uri = o.optString("uri"),
            label = o.optString("label"),
        )

        fun intent(intent: Intent, label: String) = TapAction(Type.INTENT, intent.toUri(0), label)
    }
}
