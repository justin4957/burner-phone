package com.burnerphone.detector.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.R
import com.burnerphone.detector.scanning.BluetoothScanner
import com.burnerphone.detector.scanning.LocationProvider
import com.burnerphone.detector.scanning.WiFiScanner
import com.burnerphone.detector.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Foreground service that continuously monitors WiFi, Bluetooth, and network devices
 */
class DeviceMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var wifiScanner: WiFiScanner
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var locationProvider: LocationProvider

    private var isMonitoring = false

    override fun onCreate() {
        super.onCreate()
        val app = application as BurnerPhoneApplication

        wifiScanner = WiFiScanner(this, app.database.deviceDetectionDao())
        bluetoothScanner = BluetoothScanner(this, app.database.deviceDetectionDao())
        locationProvider = LocationProvider(this)
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

        startForeground(NOTIFICATION_ID, createNotification())
        isMonitoring = true

        serviceScope.launch {
            while (isActive && isMonitoring) {
                try {
                    val location = locationProvider.getCurrentLocation()

                    // Scan WiFi networks
                    launch {
                        wifiScanner.scan(location)
                    }

                    // Scan Bluetooth devices
                    launch {
                        bluetoothScanner.scan(location)
                    }

                    // Wait for scan interval
                    delay(SCAN_INTERVAL_MS)
                } catch (e: Exception) {
                    // Log error but continue monitoring
                }
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
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

        return NotificationCompat.Builder(this, BurnerPhoneApplication.SERVICE_CHANNEL_ID)
            .setContentTitle("BurnerPhone Active")
            .setContentText("Monitoring device patterns in background")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_MONITORING = "com.burnerphone.detector.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.burnerphone.detector.STOP_MONITORING"
        private const val NOTIFICATION_ID = 1001
        private const val SCAN_INTERVAL_MS = 60_000L  // 1 minute
    }
}
