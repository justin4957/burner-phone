package com.burnerphone.detector.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.burnerphone.detector.data.converters.Converters
import kotlinx.serialization.Serializable

/**
 * Represents a detected anomaly in device appearance patterns
 */
@Entity(tableName = "anomaly_detections")
@TypeConverters(Converters::class)
data class AnomalyDetection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Anomaly metadata
    val detectedAt: Long,
    val anomalyType: AnomalyType,
    val severity: AnomalySeverity,

    // Affected devices
    val deviceAddresses: List<String>,
    val deviceType: DeviceType,

    // Statistical measures
    val anomalyScore: Double,  // 0.0 to 1.0, higher = more anomalous
    val confidenceLevel: Double,  // 0.0 to 1.0

    // Pattern details
    val description: String,
    val detectionCount: Int,  // Number of times this pattern occurred

    // Geographic data
    val locations: List<LocationPoint>,
    val geographicSpread: Double? = null,  // Distance in meters

    // Temporal data
    val timeSpan: Long,  // Duration of pattern in milliseconds
    val firstSeen: Long,
    val lastSeen: Long,

    // User actions
    val isAcknowledged: Boolean = false,
    val isFalsePositive: Boolean = false,
    val userNotes: String? = null
)

enum class AnomalyType {
    TEMPORAL_CLUSTERING,      // Same device appearing at unusual times
    GEOGRAPHIC_TRACKING,      // Same device following user across locations
    FREQUENCY_ANOMALY,        // Unusual appearance frequency
    CORRELATION_PATTERN,      // Multiple devices appearing together suspiciously
    SIGNAL_STRENGTH_ANOMALY,  // Unusual signal strength patterns
    NEW_DEVICE_CLUSTER,       // Sudden appearance of multiple new devices
    ML_BASED_ANOMALY          // Machine learning detected behavioral deviation
}

enum class AnomalySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float? = null
)
