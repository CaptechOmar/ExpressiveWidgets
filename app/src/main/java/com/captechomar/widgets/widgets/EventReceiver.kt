package com.captechomar.widgets.widgets

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.captechomar.widgets.data.BatteryReader

/** Refreshes widgets on power, boot and Bluetooth events. */
class EventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BT_BATTERY -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val level = intent.getIntExtra(EXTRA_BT_BATTERY, -1)
                if (device != null) BatteryReader.cacheLevel(context, device.address, level)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (device != null) BatteryReader.clearCachedLevel(context, device.address)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> UpdateWorker.schedule(context)
        }
        WidgetUpdater.updateAll(context)
    }

    private companion object {
        const val ACTION_BT_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BT_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
