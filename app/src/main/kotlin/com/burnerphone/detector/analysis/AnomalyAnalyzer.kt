package com.burnerphone.detector.analysis

import com.burnerphone.detector.data.dao.AnomalyDetectionDao
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.dao.WhitelistedDeviceDao
import com.burnerphone.detector.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import kotlin.math.*

/**
 * Analyzes device detection patterns for statistical anomalies
 */
class AnomalyAnalyzer(
    private val deviceDetectionDao: DeviceDetectionDao,
    private val anomalyDetectionDao: AnomalyDetectionDao,
    private val whitelistedDeviceDao: WhitelistedDeviceDao
) {

    /**
     * Run full anomaly analysis on all device data
     */
    suspend fun analyzeAll() {
        withContext(Dispatchers.Default) {
            val deviceTypes = listOf(
                DeviceType.WIFI_NETWORK,
                DeviceType.BLUETOOTH_DEVICE
            )

            for (deviceType in deviceTypes) {
                val devices = deviceDetectionDao.getUniqueDeviceAddresses(deviceType)

                for (deviceAddress in devices) {
                    analyzeDevice(deviceAddress, deviceType)
                }
            }
        }
    }

    /**
     * Analyze a specific device for anomalous patterns
     */
    private suspend fun analyzeDevice(deviceAddress: String, deviceType: DeviceType) {
        // Skip whitelisted devices
        if (whitelistedDeviceDao.isWhitelisted(deviceAddress)) {
            return
        }

        val endTime = System.currentTimeMillis()
        val startTime = endTime - ANALYSIS_WINDOW_MS

        val detections = deviceDetectionDao.getDetectionsInTimeRange(
            deviceAddress,
            startTime,
            endTime
        )

        if (detections.size < MIN_DETECTIONS_FOR_ANALYSIS) {
            return
        }

        // Analyze temporal patterns
        analyzeTemporalPatterns(deviceAddress, deviceType, detections)

        // Analyze geographic patterns
        analyzeGeographicPatterns(deviceAddress, deviceType, detections)

        // Analyze frequency patterns
        analyzeFrequencyPatterns(deviceAddress, deviceType, detections)
    }

    /**
     * Detect anomalous temporal clustering
     */
    private suspend fun analyzeTemporalPatterns(
        deviceAddress: String,
        deviceType: DeviceType,
        detections: List<DeviceDetection>
    ) {
        if (detections.size < 3) return

        // Calculate time intervals between detections
        val intervals = detections.zipWithNext { a, b ->
            abs(a.timestamp - b.timestamp)
        }

        val stats = DescriptiveStatistics()
        intervals.forEach { stats.addValue(it.toDouble()) }

        val mean = stats.mean
        val stdDev = stats.standardDeviation

        // Find clusters (intervals significantly smaller than average)
        val clusters = mutableListOf<List<DeviceDetection>>()
        var currentCluster = mutableListOf(detections.first())

        for (i in 1 until detections.size) {
            val interval = abs(detections[i].timestamp - detections[i - 1].timestamp)

            if (interval < mean - (2 * stdDev)) {
                // Part of a cluster
                currentCluster.add(detections[i])
            } else {
                if (currentCluster.size >= 3) {
                    clusters.add(currentCluster.toList())
                }
                currentCluster = mutableListOf(detections[i])
            }
        }

        if (currentCluster.size >= 3) {
            clusters.add(currentCluster)
        }

        // Create anomaly records for significant clusters
        for (cluster in clusters) {
            val anomalyScore = calculateClusterAnomalyScore(cluster, mean, stdDev)

            if (anomalyScore > ANOMALY_THRESHOLD) {
                val anomaly = AnomalyDetection(
                    detectedAt = System.currentTimeMillis(),
                    anomalyType = AnomalyType.TEMPORAL_CLUSTERING,
                    severity = calculateSeverity(anomalyScore),
                    deviceAddresses = listOf(deviceAddress),
                    deviceType = deviceType,
                    anomalyScore = anomalyScore,
                    confidenceLevel = min(anomalyScore * 1.2, 1.0),
                    description = "Device detected ${cluster.size} times in rapid succession",
                    detectionCount = cluster.size,
                    locations = cluster.mapNotNull { detection ->
                        detection.latitude?.let { lat ->
                            detection.longitude?.let { lon ->
                                LocationPoint(lat, lon, detection.timestamp, detection.accuracy)
                            }
                        }
                    },
                    timeSpan = cluster.last().timestamp - cluster.first().timestamp,
                    firstSeen = cluster.first().timestamp,
                    lastSeen = cluster.last().timestamp
                )

                anomalyDetectionDao.insert(anomaly)
            }
        }
    }

    /**
     * Detect devices following user across locations
     */
    private suspend fun analyzeGeographicPatterns(
        deviceAddress: String,
        deviceType: DeviceType,
        detections: List<DeviceDetection>
    ) {
        val detectionsWithLocation = detections.filter {
            it.latitude != null && it.longitude != null
        }

        if (detectionsWithLocation.size < 3) return

        // Calculate distances between consecutive locations
        val distances = detectionsWithLocation.zipWithNext { a, b ->
            calculateDistance(
                a.latitude!!, a.longitude!!,
                b.latitude!!, b.longitude!!
            )
        }

        // If device appears at significantly different locations, it might be tracking
        val significantDistances = distances.count { it > SIGNIFICANT_DISTANCE_METERS }

        if (significantDistances >= 2) {
            val totalDistance = distances.sum()
            val anomalyScore = min(significantDistances / 5.0, 1.0)

            if (anomalyScore > ANOMALY_THRESHOLD) {
                val anomaly = AnomalyDetection(
                    detectedAt = System.currentTimeMillis(),
                    anomalyType = AnomalyType.GEOGRAPHIC_TRACKING,
                    severity = calculateSeverity(anomalyScore),
                    deviceAddresses = listOf(deviceAddress),
                    deviceType = deviceType,
                    anomalyScore = anomalyScore,
                    confidenceLevel = min(anomalyScore * 1.1, 1.0),
                    description = "Device detected at $significantDistances different locations",
                    detectionCount = detectionsWithLocation.size,
                    locations = detectionsWithLocation.map {
                        LocationPoint(it.latitude!!, it.longitude!!, it.timestamp, it.accuracy)
                    },
                    geographicSpread = totalDistance,
                    timeSpan = detectionsWithLocation.last().timestamp - detectionsWithLocation.first().timestamp,
                    firstSeen = detectionsWithLocation.first().timestamp,
                    lastSeen = detectionsWithLocation.last().timestamp
                )

                anomalyDetectionDao.insert(anomaly)
            }
        }
    }

    /**
     * Detect unusual appearance frequency
     */
    private suspend fun analyzeFrequencyPatterns(
        deviceAddress: String,
        deviceType: DeviceType,
        detections: List<DeviceDetection>
    ) {
        if (detections.size < MIN_DETECTIONS_FOR_FREQUENCY_ANALYSIS) return

        val timeSpan = detections.last().timestamp - detections.first().timestamp
        val frequencyPerHour = (detections.size.toDouble() / timeSpan) * 3600000

        // High frequency of appearances might indicate tracking
        if (frequencyPerHour > SUSPICIOUS_FREQUENCY_PER_HOUR) {
            val anomalyScore = min(frequencyPerHour / 20.0, 1.0)

            if (anomalyScore > ANOMALY_THRESHOLD) {
                val anomaly = AnomalyDetection(
                    detectedAt = System.currentTimeMillis(),
                    anomalyType = AnomalyType.FREQUENCY_ANOMALY,
                    severity = calculateSeverity(anomalyScore),
                    deviceAddresses = listOf(deviceAddress),
                    deviceType = deviceType,
                    anomalyScore = anomalyScore,
                    confidenceLevel = min(anomalyScore * 1.15, 1.0),
                    description = "Device detected ${detections.size} times (${String.format("%.1f", frequencyPerHour)}/hour)",
                    detectionCount = detections.size,
                    locations = detections.mapNotNull { detection ->
                        detection.latitude?.let { lat ->
                            detection.longitude?.let { lon ->
                                LocationPoint(lat, lon, detection.timestamp, detection.accuracy)
                            }
                        }
                    },
                    timeSpan = timeSpan,
                    firstSeen = detections.first().timestamp,
                    lastSeen = detections.last().timestamp
                )

                anomalyDetectionDao.insert(anomaly)
            }
        }
    }

    private fun calculateClusterAnomalyScore(
        cluster: List<DeviceDetection>,
        meanInterval: Double,
        stdDevInterval: Double
    ): Double {
        val clusterTimeSpan = cluster.last().timestamp - cluster.first().timestamp
        val expectedTimeSpan = meanInterval * cluster.size

        return min(expectedTimeSpan / max(clusterTimeSpan, 1.0) / 10.0, 1.0)
    }

    private fun calculateSeverity(anomalyScore: Double): AnomalySeverity {
        return when {
            anomalyScore >= 0.8 -> AnomalySeverity.CRITICAL
            anomalyScore >= 0.6 -> AnomalySeverity.HIGH
            anomalyScore >= 0.4 -> AnomalySeverity.MEDIUM
            else -> AnomalySeverity.LOW
        }
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    companion object {
        private const val ANALYSIS_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
        private const val MIN_DETECTIONS_FOR_ANALYSIS = 3
        private const val MIN_DETECTIONS_FOR_FREQUENCY_ANALYSIS = 5
        private const val ANOMALY_THRESHOLD = 0.5
        private const val SIGNIFICANT_DISTANCE_METERS = 500.0  // 500 meters
        private const val SUSPICIOUS_FREQUENCY_PER_HOUR = 10.0
    }
}
