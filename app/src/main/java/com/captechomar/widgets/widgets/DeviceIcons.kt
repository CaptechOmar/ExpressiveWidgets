package com.captechomar.widgets.widgets

import androidx.annotation.DrawableRes
import com.captechomar.widgets.R
import com.captechomar.widgets.data.DeviceKind
import com.captechomar.widgets.data.IconChoice
import com.captechomar.widgets.data.TapAction

/** The icons a block of the battery widget can use. Material icons have a filled variant, the custom ones don't. */
object DeviceIcons {
    class Entry(val id: String, val label: String, @DrawableRes val outline: Int, @DrawableRes val filled: Int?)

    val all: List<Entry> = listOf(
        Entry("up_phone", "Phone", R.drawable.up_phone, null),
        Entry("phone_dots_side", "Phone (dots)", R.drawable.up_phone_side, R.drawable.up_phone_side_fill),
        Entry("up_airpods", "Earbuds", R.drawable.up_airpods, null),
        Entry("mobile", "Mobile", R.drawable.ms_mobile, R.drawable.ms_mobile_fill),
        Entry("mobile_3", "Mobile 3", R.drawable.ms_mobile_3, R.drawable.ms_mobile_3_fill),
        Entry("watch", "Watch", R.drawable.ms_watch, R.drawable.ms_watch_fill),
        Entry("earbuds_2", "Earbuds 2", R.drawable.ms_earbuds_2, R.drawable.ms_earbuds_2_fill),
        Entry("earbud_case", "Earbud case", R.drawable.ms_earbud_case, R.drawable.ms_earbud_case_fill),
        Entry("earbud_left", "Earbud left", R.drawable.ms_earbud_left, R.drawable.ms_earbud_left_fill),
        Entry("earbud_right", "Earbud right", R.drawable.ms_earbud_right, R.drawable.ms_earbud_right_fill),
        Entry("headphones", "Headphones", R.drawable.ms_headphones, R.drawable.ms_headphones_fill),
        Entry("home_speaker", "Speaker", R.drawable.ms_home_speaker, R.drawable.ms_home_speaker_fill),
        Entry("stadia_controller", "Controller", R.drawable.ms_stadia_controller, R.drawable.ms_stadia_controller_fill),
        Entry("bluetooth", "Bluetooth", R.drawable.ms_bluetooth, R.drawable.ms_bluetooth_fill),
    )

    fun entry(id: String): Entry = all.firstOrNull { it.id == id } ?: all.first { it.id == "bluetooth" }

    @DrawableRes
    fun res(choice: IconChoice): Int {
        val e = entry(choice.id)
        return if (choice.filled && e.filled != null) e.filled else e.outline
    }

    /** What a block gets until the user picks something else. */
    fun defaultChoice(key: String, kind: DeviceKind) = IconChoice(
        when {
            key == TapAction.PHONE -> "up_phone"
            kind == DeviceKind.WATCH -> "watch"
            kind == DeviceKind.EARBUDS || kind == DeviceKind.HEADPHONES -> "up_airpods"
            else -> "bluetooth"
        },
    )
}
