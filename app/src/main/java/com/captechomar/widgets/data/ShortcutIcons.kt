package com.captechomar.widgets.data

import com.captechomar.widgets.R

/** An icon offered for a shortcut. [id] is saved in the widget settings. */
class ShortcutIcon(val id: String, val label: String, val res: Int)

object ShortcutIcons {
    val all: List<ShortcutIcon> = listOf(
        ShortcutIcon("solar:bolt-bold", "Bolt", R.drawable.sci_solar_bolt_bold),
        ShortcutIcon("tabler:square-rounded-check", "Rounded check", R.drawable.sci_tabler_square_rounded_check),
        ShortcutIcon("cib:stadia", "Stadia", R.drawable.sci_cib_stadia),
        ShortcutIcon("lucide:circle-dollar-sign", "Dollar", R.drawable.sci_lucide_circle_dollar_sign),
        ShortcutIcon("solar:remote-controller-linear", "Remote", R.drawable.sci_solar_remote_controller_linear),
        ShortcutIcon("lucide:house", "House", R.drawable.sci_lucide_house),
        ShortcutIcon("lucide:phone", "Phone", R.drawable.sci_lucide_phone),
        ShortcutIcon("lucide:camera", "Camera", R.drawable.sci_lucide_camera),
        ShortcutIcon("lucide:flashlight", "Flashlight", R.drawable.sci_lucide_flashlight),
        ShortcutIcon("lucide:wifi", "Wifi", R.drawable.sci_lucide_wifi),
        ShortcutIcon("lucide:bluetooth", "Bluetooth", R.drawable.sci_lucide_bluetooth),
        ShortcutIcon("lucide:music", "Music", R.drawable.sci_lucide_music),
        ShortcutIcon("lucide:map-pin", "Map pin", R.drawable.sci_lucide_map_pin),
        ShortcutIcon("lucide:mail", "Mail", R.drawable.sci_lucide_mail),
        ShortcutIcon("lucide:calendar", "Calendar", R.drawable.sci_lucide_calendar),
        ShortcutIcon("lucide:clock", "Clock", R.drawable.sci_lucide_clock),
        ShortcutIcon("lucide:settings", "Settings", R.drawable.sci_lucide_settings),
        ShortcutIcon("lucide:bell", "Bell", R.drawable.sci_lucide_bell),
        ShortcutIcon("lucide:heart", "Heart", R.drawable.sci_lucide_heart),
        ShortcutIcon("lucide:star", "Star", R.drawable.sci_lucide_star),
        ShortcutIcon("lucide:shopping-cart", "Shopping cart", R.drawable.sci_lucide_shopping_cart),
        ShortcutIcon("lucide:car", "Car", R.drawable.sci_lucide_car),
        ShortcutIcon("lucide:plane", "Plane", R.drawable.sci_lucide_plane),
        ShortcutIcon("lucide:lightbulb", "Lightbulb", R.drawable.sci_lucide_lightbulb),
        ShortcutIcon("lucide:lock", "Lock", R.drawable.sci_lucide_lock),
        ShortcutIcon("lucide:play", "Play", R.drawable.sci_lucide_play),
        ShortcutIcon("lucide:message-circle", "Message circle", R.drawable.sci_lucide_message_circle),
        ShortcutIcon("lucide:globe", "Globe", R.drawable.sci_lucide_globe),
        ShortcutIcon("lucide:wallet", "Wallet", R.drawable.sci_lucide_wallet),
        ShortcutIcon("lucide:qr-code", "Qr code", R.drawable.sci_lucide_qr_code),
        ShortcutIcon("lucide:tv", "Tv", R.drawable.sci_lucide_tv),
        ShortcutIcon("lucide:gamepad-2", "Gamepad", R.drawable.sci_lucide_gamepad_2),
        ShortcutIcon("lucide:bookmark", "Bookmark", R.drawable.sci_lucide_bookmark),
        ShortcutIcon("lucide:search", "Search", R.drawable.sci_lucide_search),
        ShortcutIcon("lucide:download", "Download", R.drawable.sci_lucide_download),
    )

    fun byId(id: String): ShortcutIcon? = all.firstOrNull { it.id == id }
}
