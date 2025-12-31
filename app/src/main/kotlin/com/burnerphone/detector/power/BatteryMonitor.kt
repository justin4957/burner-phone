package com.burnerphone.detector.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors battery level and power save mode to optimize scanning behavior
 */
class BatteryMonitor(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val _scanInterval = MutableStateFlow(DEFAULT_SCAN_INTERVAL_MS)
    val scanInterval: StateFlow<Long> = _scanInterval.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    updateBatteryState(intent)
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    updatePowerSaveMode()
                }
            }
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        context.registerReceiver(batteryReceiver, filter)

        // Initialize current state
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let { updateBatteryState(it) }
        updatePowerSaveMode()
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }

    private fun updateBatteryState(intent: Intent) {
        // Get battery level
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            100
        }
        _batteryLevel.value = batteryPct

        // Get charging state
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Update scan strategy based on battery state
        updateScanStrategy()
    }

    private fun updatePowerSaveMode() {
        _isPowerSaveMode.value = powerManager.isPowerSaveMode
        updateScanStrategy()
    }

    private fun updateScanStrategy() {
        val battery = _batteryLevel.value
        val charging = _isCharging.value
        val powerSave = _isPowerSaveMode.value

        // Determine scan interval based on battery and power save state
        _scanInterval.value = when {
            charging -> CHARGING_SCAN_INTERVAL_MS
            powerSave -> POWER_SAVE_SCAN_INTERVAL_MS
            battery >= 80 -> DEFAULT_SCAN_INTERVAL_MS
            battery >= 50 -> MODERATE_SCAN_INTERVAL_MS
            battery >= 20 -> LOW_BATTERY_SCAN_INTERVAL_MS
            else -> CRITICAL_BATTERY_SCAN_INTERVAL_MS
        }

        // Disable Bluetooth scanning when battery is low or power save is on
        _isBluetoothEnabled.value = when {
            charging -> true
            powerSave -> false
            battery >= 30 -> true
            else -> false
        }
    }

    /**
     * Get current battery statistics
     */
    fun getBatteryStats(): BatteryStats {
        return BatteryStats(
            level = _batteryLevel.value,
            isCharging = _isCharging.value,
            isPowerSaveMode = _isPowerSaveMode.value,
            scanInterval = _scanInterval.value,
            isBluetoothEnabled = _isBluetoothEnabled.value
        )
    }

    /**
     * Check if device is in Doze mode (requires API 23+)
     */
    fun isDeviceIdleMode(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }

    companion object {
        // Scan intervals in milliseconds
        const val CHARGING_SCAN_INTERVAL_MS = 30_000L          // 30 seconds when charging
        const val DEFAULT_SCAN_INTERVAL_MS = 60_000L           // 1 minute (normal)
        const val MODERATE_SCAN_INTERVAL_MS = 120_000L         // 2 minutes (50-80% battery)
        const val LOW_BATTERY_SCAN_INTERVAL_MS = 300_000L      // 5 minutes (20-50% battery)
        const val CRITICAL_BATTERY_SCAN_INTERVAL_MS = 600_000L // 10 minutes (<20% battery)
        const val POWER_SAVE_SCAN_INTERVAL_MS = 600_000L       // 10 minutes (power save mode)
    }
}

data class BatteryStats(
    val level: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
    val scanInterval: Long,
    val isBluetoothEnabled: Boolean
)
