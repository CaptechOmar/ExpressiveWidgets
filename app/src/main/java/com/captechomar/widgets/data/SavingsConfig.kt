package com.captechomar.widgets.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** One savings goal and how its widget looks. Amounts are in whatever currency [currency] says. */
data class SavingsConfig(
    val name: String = "My goal",
    val emoji: String = "🎯",
    val currency: String = "$",
    val target: Double = 1000.0,
    val saved: Double = 0.0,
    /** "yyyy-MM" of the month [monthStart] belongs to. */
    val monthKey: String = "",
    /** The saved amount at the start of [monthKey], so progress added this month can be shown. */
    val monthStart: Double = 0.0,
    val expressive: Boolean = true,
    val quickCalc: Boolean = true,
    val monthly: Boolean = true,
    val debtMode: Boolean = false,
    val estimated: Boolean = true,
    val monoEmoji: Boolean = false,
    /** File name (in the app's files folder) of the background picture, or empty. */
    val bgFile: String = "",
) {
    val remaining: Double get() = max(0.0, target - saved)

    /** Whole percent of the target that is saved. */
    val percent: Int get() = if (target > 0) (saved / target * 100).toInt().coerceIn(0, 100) else 0

    /** Progress added in the current month. A new month that hasn't been rolled over yet counts as zero. */
    fun monthDelta(now: String = currentMonth()): Double = if (monthKey == now) saved - monthStart else 0.0

    /** Starts a new month's baseline when the month has changed. */
    fun rolled(now: String = currentMonth()): SavingsConfig =
        if (monthKey == now) this else copy(monthKey = now, monthStart = saved)

    fun withSaved(value: Double): SavingsConfig = rolled().copy(saved = max(0.0, value))

    fun toJson(): String = JSONObject()
        .put("name", name).put("emoji", emoji).put("currency", currency)
        .put("target", target).put("saved", saved)
        .put("monthKey", monthKey).put("monthStart", monthStart)
        .put("expressive", expressive).put("quickCalc", quickCalc).put("monthly", monthly)
        .put("debtMode", debtMode).put("estimated", estimated).put("monoEmoji", monoEmoji)
        .put("bgFile", bgFile)
        .toString()

    companion object {
        fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        fun fromJson(json: String?): SavingsConfig {
            val d = SavingsConfig()
            if (json.isNullOrBlank()) return d
            return try {
                val o = JSONObject(json)
                SavingsConfig(
                    name = o.optString("name", d.name),
                    emoji = o.optString("emoji", d.emoji),
                    currency = o.optString("currency", d.currency),
                    target = o.optDouble("target", d.target),
                    saved = o.optDouble("saved", d.saved),
                    monthKey = o.optString("monthKey", d.monthKey),
                    monthStart = o.optDouble("monthStart", d.monthStart),
                    expressive = o.optBoolean("expressive", d.expressive),
                    quickCalc = o.optBoolean("quickCalc", d.quickCalc),
                    monthly = o.optBoolean("monthly", d.monthly),
                    debtMode = o.optBoolean("debtMode", d.debtMode),
                    estimated = o.optBoolean("estimated", d.estimated),
                    monoEmoji = o.optBoolean("monoEmoji", d.monoEmoji),
                    bgFile = o.optString("bgFile", d.bgFile),
                )
            } catch (_: Throwable) {
                d
            }
        }
    }
}
