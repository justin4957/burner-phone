package com.burnerphone.detector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.burnerphone.detector.data.AppDatabase
import com.burnerphone.detector.workers.AnomalyAnalysisWorker
import com.burnerphone.detector.workers.DataCleanupWorker
import java.util.concurrent.TimeUnit

class BurnerPhoneApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleDataCleanup()
        scheduleAnomalyAnalysis()
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

    /**
     * Schedule periodic data cleanup to run daily
     * Runs during low-usage periods with no strict constraints
     */
    private fun scheduleDataCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val cleanupWorkRequest = PeriodicWorkRequestBuilder<DataCleanupWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(4, TimeUnit.HOURS) // Initial delay to avoid running immediately
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DataCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already scheduled
            cleanupWorkRequest
        )
    }

    /**
     * Schedule periodic anomaly analysis to run every 6 hours
     * Analyzes device patterns to detect surveillance and tracking
     */
    private fun scheduleAnomalyAnalysis() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val analysisWorkRequest = PeriodicWorkRequestBuilder<AnomalyAnalysisWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.MINUTES) // Initial delay to avoid running immediately
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AnomalyAnalysisWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already scheduled
            analysisWorkRequest
        )
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "device_monitoring_service"
        const val ALERT_CHANNEL_ID = "anomaly_alerts"
    }
}
