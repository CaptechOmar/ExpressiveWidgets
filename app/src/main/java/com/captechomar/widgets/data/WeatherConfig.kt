package com.captechomar.widgets.data

import org.json.JSONObject

/** Where a weather widget gets its forecast and how it shows it. */
data class WeatherConfig(
    /** Name shown on the widget; empty until a city is picked. */
    val city: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val hasPlace: Boolean = false,
    /** Follow the device location instead of the picked city. */
    val useLocation: Boolean = false,
    /** °F and mph instead of °C and km/h. */
    val imperial: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("city", city).put("lat", lat).put("lon", lon).put("hasPlace", hasPlace)
        .put("useLocation", useLocation).put("imperial", imperial).toString()

    companion object {
        fun fromJson(json: String?): WeatherConfig {
            if (json == null) return WeatherConfig()
            return try {
                val o = JSONObject(json)
                WeatherConfig(
                    city = o.optString("city"), lat = o.optDouble("lat"), lon = o.optDouble("lon"),
                    hasPlace = o.optBoolean("hasPlace"), useLocation = o.optBoolean("useLocation"),
                    imperial = o.optBoolean("imperial"),
                )
            } catch (_: Throwable) {
                WeatherConfig()
            }
        }
    }
}
