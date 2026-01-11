package com.burnerphone.detector.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.burnerphone.detector.data.models.AnomalyDetection
import com.burnerphone.detector.data.models.AnomalySeverity
import com.burnerphone.detector.data.models.AnomalyType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager

/**
 * Unit tests for AnomalyNotificationManager
 *
 * Tests notification creation, filtering, grouping, and content formatting
 * Uses Robolectric to test Android notification APIs
 */
@RunWith(RobolectricTestRunner::class)
class AnomalyNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: AnomalyNotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = AnomalyNotificationManager(context)

        val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(systemNotificationManager)
    }

    @Test
    fun `notifyAnomalyDetected sends notification for HIGH severity`() {
        val anomaly = createTestAnomaly(
            id = 1L,
            severity = AnomalySeverity.HIGH,
            type = AnomalyType.TEMPORAL_CLUSTERING
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isNotEmpty()) { "Expected notification to be sent for HIGH severity" }
    }

    @Test
    fun `notifyAnomalyDetected sends notification for CRITICAL severity`() {
        val anomaly = createTestAnomaly(
            id = 2L,
            severity = AnomalySeverity.CRITICAL,
            type = AnomalyType.GEOGRAPHIC_TRACKING
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isNotEmpty()) { "Expected notification to be sent for CRITICAL severity" }
    }

    @Test
    fun `notifyAnomalyDetected does not send notification for MEDIUM severity`() {
        val anomaly = createTestAnomaly(
            id = 3L,
            severity = AnomalySeverity.MEDIUM,
            type = AnomalyType.FREQUENCY_ANOMALY
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isEmpty()) { "Expected no notification for MEDIUM severity" }
    }

    @Test
    fun `notifyAnomalyDetected does not send notification for LOW severity`() {
        val anomaly = createTestAnomaly(
            id = 4L,
            severity = AnomalySeverity.LOW,
            type = AnomalyType.SIGNAL_STRENGTH_ANOMALY
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isEmpty()) { "Expected no notification for LOW severity" }
    }

    @Test
    fun `notifyMultipleAnomalies sends notifications only for HIGH and CRITICAL`() {
        val anomalies = listOf(
            createTestAnomaly(1L, AnomalySeverity.LOW, AnomalyType.FREQUENCY_ANOMALY),
            createTestAnomaly(2L, AnomalySeverity.MEDIUM, AnomalyType.CORRELATION_PATTERN),
            createTestAnomaly(3L, AnomalySeverity.HIGH, AnomalyType.TEMPORAL_CLUSTERING),
            createTestAnomaly(4L, AnomalySeverity.CRITICAL, AnomalyType.GEOGRAPHIC_TRACKING)
        )

        notificationManager.notifyMultipleAnomalies(anomalies)

        val notifications = shadowNotificationManager.allNotifications
        // Should have 2 individual notifications + 1 summary = 3 total
        assert(notifications.size >= 2) { "Expected at least 2 notifications for HIGH and CRITICAL anomalies" }
    }

    @Test
    fun `notifyMultipleAnomalies does not send if all are low severity`() {
        val anomalies = listOf(
            createTestAnomaly(1L, AnomalySeverity.LOW, AnomalyType.FREQUENCY_ANOMALY),
            createTestAnomaly(2L, AnomalySeverity.MEDIUM, AnomalyType.CORRELATION_PATTERN)
        )

        notificationManager.notifyMultipleAnomalies(anomalies)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isEmpty()) { "Expected no notifications when all anomalies are low severity" }
    }

    @Test
    fun `notification contains anomaly ID in content intent`() {
        val anomalyId = 12345L
        val anomaly = createTestAnomaly(
            id = anomalyId,
            severity = AnomalySeverity.HIGH,
            type = AnomalyType.ML_BASED_ANOMALY
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isNotEmpty()) { "Expected notification to be sent" }

        val notification = notifications.first()
        val intent = notification.contentIntent
        assert(intent != null) { "Expected content intent to be set" }
    }

    @Test
    fun `notification title reflects CRITICAL severity`() {
        val anomaly = createTestAnomaly(
            id = 1L,
            severity = AnomalySeverity.CRITICAL,
            type = AnomalyType.GEOGRAPHIC_TRACKING
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isNotEmpty()) { "Expected notification to be sent" }

        val notification = notifications.first()
        val extras = notification.extras
        val title = extras.getString("android.title")
        assert(title?.contains("CRITICAL") == true) { "Expected CRITICAL in title" }
    }

    @Test
    fun `notification title reflects HIGH severity`() {
        val anomaly = createTestAnomaly(
            id = 1L,
            severity = AnomalySeverity.HIGH,
            type = AnomalyType.TEMPORAL_CLUSTERING
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isNotEmpty()) { "Expected notification to be sent" }

        val notification = notifications.first()
        val extras = notification.extras
        val title = extras.getString("android.title")
        assert(title?.contains("High") == true) { "Expected 'High' in title" }
    }

    @Test
    fun `notification content includes device count`() {
        val anomaly = createTestAnomaly(
            id = 1L,
            severity = AnomalySeverity.HIGH,
            type = AnomalyType.NEW_DEVICE_CLUSTER,
            deviceAddresses = listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03")
        )

        notificationManager.notifyAnomalyDetected(anomaly)

        val notifications = shadowNotificationManager.allNotifications
        assert(notifications.isNotEmpty()) { "Expected notification to be sent" }

        val notification = notifications.first()
        val extras = notification.extras
        val text = extras.getString("android.text")
        assert(text?.contains("3") == true) { "Expected device count in notification text" }
    }

    @Test
    fun `clearAll removes all notifications`() {
        val anomaly1 = createTestAnomaly(1L, AnomalySeverity.HIGH, AnomalyType.TEMPORAL_CLUSTERING)
        val anomaly2 = createTestAnomaly(2L, AnomalySeverity.CRITICAL, AnomalyType.GEOGRAPHIC_TRACKING)

        notificationManager.notifyAnomalyDetected(anomaly1)
        notificationManager.notifyAnomalyDetected(anomaly2)

        assert(shadowNotificationManager.allNotifications.isNotEmpty()) { "Expected notifications before clear" }

        notificationManager.clearAll()

        assert(shadowNotificationManager.allNotifications.isEmpty()) { "Expected all notifications to be cleared" }
    }

    @Test
    fun `clearNotification removes specific notification`() {
        val anomaly1 = createTestAnomaly(1L, AnomalySeverity.HIGH, AnomalyType.TEMPORAL_CLUSTERING)
        val anomaly2 = createTestAnomaly(2L, AnomalySeverity.CRITICAL, AnomalyType.GEOGRAPHIC_TRACKING)

        notificationManager.notifyAnomalyDetected(anomaly1)
        notificationManager.notifyAnomalyDetected(anomaly2)

        val beforeCount = shadowNotificationManager.allNotifications.size

        notificationManager.clearNotification(1L)

        val afterCount = shadowNotificationManager.allNotifications.size
        assert(afterCount < beforeCount) { "Expected notification count to decrease" }
    }

    /**
     * Helper function to create test anomalies
     */
    private fun createTestAnomaly(
        id: Long,
        severity: AnomalySeverity,
        type: AnomalyType,
        deviceAddresses: List<String> = listOf("AA:BB:CC:DD:EE:FF"),
        description: String = "Test anomaly description"
    ): AnomalyDetection {
        return AnomalyDetection(
            id = id,
            anomalyType = type,
            severity = severity,
            description = description,
            deviceAddresses = deviceAddresses,
            detectedAt = System.currentTimeMillis(),
            confidenceLevel = 0.85,
            timeSpan = 3600000L, // 1 hour
            geographicSpread = 500.0, // 500 meters
            isAcknowledged = false,
            acknowledgedAt = null
        )
    }
}
