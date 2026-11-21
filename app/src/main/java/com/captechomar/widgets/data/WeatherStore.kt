package com.captechomar.widgets.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** What the widget shows, as last fetched. Temperatures are in °C and wind in km/h; the renderer converts. */
data class Weather(
    val fetchedAt: Long,
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Double,
    val windDir: Int,
)

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Fetches weather and city search results from Open-Meteo (no key needed) and keeps the last result per widget. */
object WeatherStore {
    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    private fun key(cfg: WeatherConfig) = "w%.2f,%.2f".format(Locale.US, cfg.lat, cfg.lon)

    /** The last result stored for this place, or null if none was fetched yet. */
    fun cached(context: Context, cfg: WeatherConfig): Weather? = try {
        if (!cfg.hasPlace) null else {
            val o = JSONObject(sp(context).getString(key(cfg), null) ?: return null)
            Weather(o.getLong("at"), o.getDouble("t"), o.getInt("c"), o.getBoolean("d"), o.getInt("h"), o.getDouble("w"), o.getInt("wd"))
        }
    } catch (_: Throwable) {
        null
    }

    private fun save(context: Context, cfg: WeatherConfig, f: Weather) {
        val o = JSONObject().put("at", f.fetchedAt).put("t", f.tempC).put("c", f.code).put("d", f.isDay)
            .put("h", f.humidity).put("w", f.windKmh).put("wd", f.windDir)
        sp(context).edit().putString(key(cfg), o.toString()).apply()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** City search. Blocking: call from a background thread. */
    fun search(query: String): List<Place> = try {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val arr = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=en&format=json"))
            .optJSONArray("results")
        if (arr == null) emptyList() else (0 until arr.length()).map {
            val r = arr.getJSONObject(it)
            Place(
                r.getString("name"),
                listOf(r.optString("admin1"), r.optString("country")).filter { s -> s.isNotBlank() }.joinToString(", "),
                r.getDouble("latitude"), r.getDouble("longitude"),
            )
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** The last known device position, if the location permission is granted. */
    @SuppressLint("MissingPermission")
    fun deviceLocation(context: Context): Pair<Double, Double>? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    } catch (_: Throwable) {
        null
    }

    private fun placeName(context: Context, lat: Double, lon: Double): String = try {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: ""
    } catch (_: Throwable) {
        ""
    }

    /**
     * Fetches the current weather for a widget's settings and stores it. Returns the settings to keep
     * (the device position is remembered so it still works when the app is in the background).
     * Blocking: call from a background thread.
     */
    fun refresh(context: Context, cfg: WeatherConfig, maxAgeMs: Long = 0L, fix: Pair<Double, Double>? = null): WeatherConfig {
        var place = cfg
        if (cfg.useLocation) {
            (fix ?: deviceLocation(context))?.let { (la, lo) ->
                val name = placeName(context, la, lo).ifBlank { cfg.city.ifBlank { "My location" } }
                place = cfg.copy(lat = la, lon = lo, hasPlace = true, city = name)
            }
        }
        if (!place.hasPlace) return place
        val old = cached(context, place)
        if (old != null && System.currentTimeMillis() - old.fetchedAt < maxAgeMs) return place
        try {
            val json = JSONObject(
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m" +
                        "&wind_speed_unit=kmh&timezone=auto"
                )
            ).getJSONObject("current")
            save(
                context, place,
                Weather(
                    System.currentTimeMillis(), json.getDouble("temperature_2m"), json.getInt("weather_code"),
                    json.getInt("is_day") == 1, json.getInt("relative_humidity_2m"),
                    json.getDouble("wind_speed_10m"), json.getInt("wind_direction_10m"),
                )
            )
        } catch (_: Throwable) {
            // keep showing the last result when offline
        }
        return place
    }
}
