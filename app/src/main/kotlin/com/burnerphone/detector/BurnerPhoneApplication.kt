package com.burnerphone.detector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.burnerphone.detector.data.AppDatabase

class BurnerPhoneApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Device Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring of WiFi, Bluetooth, and network devices"
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Anomaly Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for detected surveillance patterns"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "device_monitoring_service"
        const val ALERT_CHANNEL_ID = "anomaly_alerts"
    }
}
