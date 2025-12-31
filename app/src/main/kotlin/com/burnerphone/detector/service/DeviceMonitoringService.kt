package com.burnerphone.detector.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.R
import com.burnerphone.detector.power.BatteryMonitor
import com.burnerphone.detector.scanning.BluetoothScanner
import com.burnerphone.detector.scanning.LocationProvider
import com.burnerphone.detector.scanning.WiFiScanner
import com.burnerphone.detector.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Foreground service that continuously monitors WiFi, Bluetooth, and network devices
 * with power-aware adaptive scanning
 */
class DeviceMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var wifiScanner: WiFiScanner
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var locationProvider: LocationProvider
    private lateinit var batteryMonitor: BatteryMonitor

    private var isMonitoring = false
    private var scanJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as BurnerPhoneApplication

        wifiScanner = WiFiScanner(this, app.database.deviceDetectionDao())
        bluetoothScanner = BluetoothScanner(this, app.database.deviceDetectionDao())
        locationProvider = LocationProvider(this)
        batteryMonitor = BatteryMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return

        batteryMonitor.startMonitoring()
        startForeground(NOTIFICATION_ID, createNotification())
        isMonitoring = true

        // Start adaptive scanning based on battery state
        scanJob = serviceScope.launch {
            while (isActive && isMonitoring) {
                try {
                    // Check if device is in Doze mode
                    if (batteryMonitor.isDeviceIdleMode()) {
                        // In Doze mode, wait longer before next scan
                        delay(batteryMonitor.scanInterval.value * 2)
                        continue
                    }

                    val batteryStats = batteryMonitor.getBatteryStats()

                    // Get location with appropriate accuracy based on battery
                    val location = if (batteryStats.level < 30 && !batteryStats.isCharging) {
                        locationProvider.getCurrentLocation(useCoarseLocation = true)
                    } else {
                        locationProvider.getCurrentLocation()
                    }

                    // Always scan WiFi (low power)
                    launch {
                        wifiScanner.scan(location)
                    }

                    // Conditionally scan Bluetooth based on battery
                    if (batteryStats.isBluetoothEnabled) {
                        launch {
                            bluetoothScanner.scan(location)
                        }
                    }

                    // Update notification with current battery-aware status
                    updateNotification()

                    // Use adaptive scan interval from battery monitor
                    delay(batteryMonitor.scanInterval.value)
                } catch (e: Exception) {
                    // Log error but continue monitoring
                    delay(batteryMonitor.scanInterval.value)
                }
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        scanJob?.cancel()
        batteryMonitor.stopMonitoring()
        serviceScope.coroutineContext.cancelChildren()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val batteryStats = if (::batteryMonitor.isInitialized) {
            batteryMonitor.getBatteryStats()
        } else {
            null
        }

        val statusText = buildNotificationText(batteryStats)

        return NotificationCompat.Builder(this, BurnerPhoneApplication.SERVICE_CHANNEL_ID)
            .setContentTitle("BurnerPhone Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotificationText(batteryStats: com.burnerphone.detector.power.BatteryStats?): String {
        if (batteryStats == null) {
            return "Monitoring device patterns in background"
        }

        return when {
            batteryStats.isCharging -> "Monitoring (Charging - Fast mode)"
            batteryStats.isPowerSaveMode -> "Monitoring (Power save mode)"
            batteryStats.level < 20 -> "Monitoring (Low battery - Reduced scanning)"
            batteryStats.level < 50 -> "Monitoring (Battery saver mode)"
            else -> "Monitoring device patterns"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::batteryMonitor.isInitialized) {
            batteryMonitor.stopMonitoring()
        }
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_MONITORING = "com.burnerphone.detector.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.burnerphone.detector.STOP_MONITORING"
        private const val NOTIFICATION_ID = 1001
    }
}
