package com.captechomar.widgets.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build

data class PhoneBattery(val percent: Int, val charging: Boolean)

/** [percent] is -1 when the device is connected but doesn't report a battery level. */
data class BtBattery(val name: String, val address: String, val percent: Int, val isAudio: Boolean)

object BatteryReader {

    fun phone(context: Context): PhoneBattery {
        val i = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else 0
        return PhoneBattery(pct, charging)
    }

    fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Paired devices, used for the device picker. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<BtBattery> {
        if (!hasBluetoothPermission(context)) return emptyList()
        return try {
            adapter(context)?.bondedDevices.orEmpty().map { it.toBattery(context, level = -1) }
                .sortedWith(compareByDescending<BtBattery> { it.isAudio }.thenBy { it.name })
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Currently connected devices with their battery level where the system exposes one. */
    @SuppressLint("MissingPermission")
    fun connectedDevices(context: Context): List<BtBattery> {
        if (!hasBluetoothPermission(context)) return emptyList()
        return try {
            val adapter = adapter(context) ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices.orEmpty()
                .filter { isConnected(it) }
                .map { it.toBattery(context, level = readLevel(context, it)) }
                // a device that Gadgetbridge manages usually has no level in the system, but Gadgetbridge knows it
                .map { if (it.percent < 0) it.copy(percent = GadgetbridgeStore.levelFor(context, it.name)) else it }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Most devices drawn next to the phone. */
    const val MAX_DEVICES = 4

    /**
     * The devices to draw next to the phone. With no devices chosen it is the automatic pick (one audio device);
     * otherwise every chosen device that is currently connected, in the order they were chosen.
     */
    fun devices(context: Context, addresses: List<String>): List<BtBattery> {
        if (addresses.isEmpty()) return listOfNotNull(earphones(context, null))
        val connected by lazy { connectedDevices(context) }
        val gadgetbridge = GadgetbridgeStore.current(context)
        return addresses.mapNotNull { address ->
            if (GadgetbridgeStore.isGbAddress(address)) {
                val name = GadgetbridgeStore.nameOf(address)
                gadgetbridge.firstOrNull { it.name == name }?.let { BtBattery(it.name, address, it.percent, isAudio = false) }
            } else {
                connected.firstOrNull { it.address == address }
            }
        }.distinctBy { it.address }.take(MAX_DEVICES)
    }

    /**
     * Picks the device to show next to the phone: the chosen one if it is connected, otherwise the best
     * connected audio device. Returns null when nothing is connected, and the widget then shows the phone alone.
     */
    fun earphones(context: Context, preferredAddress: String?): BtBattery? {
        // a device picked from Gadgetbridge's devices
        if (preferredAddress != null && GadgetbridgeStore.isGbAddress(preferredAddress)) {
            val name = GadgetbridgeStore.nameOf(preferredAddress)
            return GadgetbridgeStore.current(context).firstOrNull { it.name == name }
                ?.let { BtBattery(it.name, preferredAddress, it.percent, isAudio = false) }
        }
        val connected = connectedDevices(context)
        if (preferredAddress != null) return connected.firstOrNull { it.address == preferredAddress }
        return connected.firstOrNull { it.isAudio && it.percent >= 0 }
            ?: connected.firstOrNull { it.isAudio }
            ?: connected.firstOrNull { it.percent >= 0 }
    }

    // ---- cache of levels pushed to us by the system's battery-level broadcast ----

    private fun cache(context: Context) =
        context.applicationContext.getSharedPreferences("bt_levels", Context.MODE_PRIVATE)

    fun cacheLevel(context: Context, address: String, level: Int) {
        if (level in 0..100) cache(context).edit().putInt(address, level).apply()
    }

    fun clearCachedLevel(context: Context, address: String) {
        cache(context).edit().remove(address).apply()
    }

    private fun adapter(context: Context) =
        context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter

    private fun isConnected(device: BluetoothDevice): Boolean = try {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as? Boolean ?: false
    } catch (_: Throwable) {
        false
    }

    // BluetoothDevice#getBatteryLevel is hidden API; fall back to the cached broadcast value.
    private fun readLevel(context: Context, device: BluetoothDevice): Int {
        val reflected = try {
            BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int ?: -1
        } catch (_: Throwable) {
            -1
        }
        if (reflected in 0..100) return reflected
        return cache(context).getInt(device.address, -1)
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toBattery(context: Context, level: Int): BtBattery {
        val audio = try {
            bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        } catch (_: Throwable) {
            false
        }
        val label = try { name } catch (_: Throwable) { null }
        return BtBattery(label ?: address, address, level, audio)
    }
}
