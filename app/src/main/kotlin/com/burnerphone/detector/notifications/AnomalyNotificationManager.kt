package com.burnerphone.detector.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.R
import com.burnerphone.detector.data.models.AnomalyDetection
import com.burnerphone.detector.data.models.AnomalySeverity
import com.burnerphone.detector.data.models.AnomalyType
import com.burnerphone.detector.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages notifications for detected surveillance anomalies
 *
 * Features:
 * - Sends notifications for HIGH and CRITICAL severity anomalies
 * - Groups multiple anomalies together
 * - Provides detailed information in expandable notifications
 * - Deep links to app when tapped
 * - Respects user notification preferences
 */
class AnomalyNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Send notification for a detected anomaly
     * Only sends for HIGH and CRITICAL severity levels
     */
    fun notifyAnomalyDetected(anomaly: AnomalyDetection) {
        // Only notify for high severity anomalies
        if (anomaly.severity != AnomalySeverity.HIGH && anomaly.severity != AnomalySeverity.CRITICAL) {
            return
        }

        val notification = buildAnomalyNotification(anomaly)
        notificationManager.notify(anomaly.id.toInt(), notification)

        // Also update the summary notification for grouped notifications
        updateSummaryNotification()
    }

    /**
     * Send notifications for multiple anomalies at once
     * Useful when batch processing anomalies
     */
    fun notifyMultipleAnomalies(anomalies: List<AnomalyDetection>) {
        val highSeverityAnomalies = anomalies.filter {
            it.severity == AnomalySeverity.HIGH || it.severity == AnomalySeverity.CRITICAL
        }

        if (highSeverityAnomalies.isEmpty()) {
            return
        }

        // Send individual notifications for each anomaly
        highSeverityAnomalies.forEach { anomaly ->
            val notification = buildAnomalyNotification(anomaly)
            notificationManager.notify(anomaly.id.toInt(), notification)
        }

        // Update summary notification
        updateSummaryNotification()
    }

    /**
     * Build a notification for a specific anomaly
     */
    private fun buildAnomalyNotification(anomaly: AnomalyDetection): android.app.Notification {
        // Create intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ANOMALY_ID, anomaly.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            anomaly.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification content
        val title = buildNotificationTitle(anomaly)
        val content = buildNotificationContent(anomaly)
        val bigText = buildExpandedContent(anomaly)

        return NotificationCompat.Builder(context, BurnerPhoneApplication.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setBigContentTitle(title)
            )
            .setPriority(getPriority(anomaly.severity))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .build()
    }

    /**
     * Update the summary notification for grouped anomalies
     */
    private fun updateSummaryNotification() {
        val summaryNotification = NotificationCompat.Builder(context, BurnerPhoneApplication.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Surveillance Alerts")
            .setContentText("Multiple anomalies detected")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setBigContentTitle("Active Surveillance Alerts")
                    .setSummaryText("Tap to view details")
            )
            .setGroup(NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    /**
     * Build notification title based on anomaly type and severity
     */
    private fun buildNotificationTitle(anomaly: AnomalyDetection): String {
        val severityPrefix = when (anomaly.severity) {
            AnomalySeverity.CRITICAL -> "🚨 CRITICAL"
            AnomalySeverity.HIGH -> "⚠️ High Alert"
            else -> "Alert"
        }

        val typeLabel = formatAnomalyType(anomaly.anomalyType)
        return "$severityPrefix: $typeLabel"
    }

    /**
     * Build short notification content
     */
    private fun buildNotificationContent(anomaly: AnomalyDetection): String {
        val deviceCount = anomaly.deviceAddresses.size
        val deviceLabel = if (deviceCount == 1) "device" else "devices"
        return "$deviceCount $deviceLabel detected - ${anomaly.description}"
    }

    /**
     * Build expanded notification content with details
     */
    private fun buildExpandedContent(anomaly: AnomalyDetection): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val detectedTime = dateFormat.format(Date(anomaly.detectedAt))

        return buildString {
            append(anomaly.description)
            append("\n\n")
            append("📍 Devices: ${anomaly.deviceAddresses.size}\n")
            append("⏰ Detected: $detectedTime\n")
            append("📊 Confidence: ${(anomaly.confidenceLevel * 100).toInt()}%\n")

            if (anomaly.geographicSpread != null) {
                append("🌍 Spread: ${formatDistance(anomaly.geographicSpread)}\n")
            }

            val durationMinutes = (anomaly.timeSpan / 60000).toInt()
            if (durationMinutes > 0) {
                append("⏱️ Duration: $durationMinutes minutes\n")
            }

            append("\nTap to view full details")
        }
    }

    /**
     * Format anomaly type for display
     */
    private fun formatAnomalyType(type: AnomalyType): String {
        return when (type) {
            AnomalyType.TEMPORAL_CLUSTERING -> "Temporal Clustering"
            AnomalyType.GEOGRAPHIC_TRACKING -> "Geographic Tracking"
            AnomalyType.FREQUENCY_ANOMALY -> "Frequency Anomaly"
            AnomalyType.CORRELATION_PATTERN -> "Device Correlation"
            AnomalyType.SIGNAL_STRENGTH_ANOMALY -> "Signal Anomaly"
            AnomalyType.NEW_DEVICE_CLUSTER -> "New Device Cluster"
            AnomalyType.ML_BASED_ANOMALY -> "ML Detected Anomaly"
        }
    }

    /**
     * Format distance in human-readable format
     */
    private fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()}m"
            else -> String.format("%.1fkm", meters / 1000)
        }
    }

    /**
     * Get notification priority based on severity
     */
    private fun getPriority(severity: AnomalySeverity): Int {
        return when (severity) {
            AnomalySeverity.CRITICAL -> NotificationCompat.PRIORITY_MAX
            AnomalySeverity.HIGH -> NotificationCompat.PRIORITY_HIGH
            AnomalySeverity.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
            AnomalySeverity.LOW -> NotificationCompat.PRIORITY_LOW
        }
    }

    /**
     * Clear all anomaly notifications
     */
    fun clearAll() {
        notificationManager.cancelAll()
    }

    /**
     * Clear a specific anomaly notification
     */
    fun clearNotification(anomalyId: Long) {
        notificationManager.cancel(anomalyId.toInt())
    }

    companion object {
        const val EXTRA_ANOMALY_ID = "anomaly_id"
        private const val NOTIFICATION_GROUP = "anomaly_alerts"
        private const val SUMMARY_NOTIFICATION_ID = 0
    }
}
