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

                // Analyze cross-device patterns
                analyzeCorrelationPatterns(deviceType)
                analyzeDeviceClusters(deviceType)
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
     * Detect devices that consistently appear together (correlation patterns)
     */
    private suspend fun analyzeCorrelationPatterns(deviceType: DeviceType) {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - ANALYSIS_WINDOW_MS

        val allDevices = deviceDetectionDao.getUniqueDeviceAddresses(deviceType)
        val nonWhitelistedDevices = allDevices.filter {
            !whitelistedDeviceDao.isWhitelisted(it)
        }

        if (nonWhitelistedDevices.size < 2) return

        // Get all detections for the time period
        val allDetections = deviceDetectionDao.getAllDetectionsInTimeRange(
            deviceType,
            startTime,
            endTime
        )

        // Group detections by time windows
        val timeWindows = groupDetectionsByTimeWindow(allDetections, CORRELATION_TIME_WINDOW_MS)

        // Calculate co-occurrence for each device pair
        val devicePairs = mutableMapOf<Pair<String, String>, CoOccurrenceData>()

        for (window in timeWindows) {
            val devicesInWindow = window.map { it.deviceAddress }.distinct()

            // Create pairs from devices in this window
            for (i in devicesInWindow.indices) {
                for (j in i + 1 until devicesInWindow.size) {
                    val device1 = devicesInWindow[i]
                    val device2 = devicesInWindow[j]

                    // Skip whitelisted devices
                    if (whitelistedDeviceDao.isWhitelisted(device1) ||
                        whitelistedDeviceDao.isWhitelisted(device2)) {
                        continue
                    }

                    val pair = if (device1 < device2) {
                        Pair(device1, device2)
                    } else {
                        Pair(device2, device1)
                    }

                    val data = devicePairs.getOrPut(pair) {
                        CoOccurrenceData(
                            device1 = pair.first,
                            device2 = pair.second,
                            coOccurrences = 0,
                            timestamps = mutableListOf(),
                            locations = mutableListOf()
                        )
                    }

                    data.coOccurrences++
                    data.timestamps.add(window.first().timestamp)

                    // Add location if available
                    window.firstOrNull { it.latitude != null && it.longitude != null }?.let {
                        data.locations.add(LocationPoint(it.latitude!!, it.longitude!!, it.timestamp, it.accuracy))
                    }
                }
            }
        }

        // Calculate correlation coefficient and create anomalies
        for ((pair, data) in devicePairs) {
            if (data.coOccurrences < MIN_COOCCURRENCES) continue

            // Calculate how often these devices appear together vs independently
            val device1Detections = allDetections.count { it.deviceAddress == pair.first }
            val device2Detections = allDetections.count { it.deviceAddress == pair.second }
            val totalWindows = timeWindows.size.toDouble()

            // Correlation score: how often they appear together relative to independent appearances
            val correlationCoefficient = data.coOccurrences.toDouble() /
                min(device1Detections.toDouble(), device2Detections.toDouble())

            val anomalyScore = min(correlationCoefficient * data.coOccurrences / 5.0, 1.0)

            if (anomalyScore > ANOMALY_THRESHOLD && correlationCoefficient > MIN_CORRELATION_COEFFICIENT) {
                val anomaly = AnomalyDetection(
                    detectedAt = System.currentTimeMillis(),
                    anomalyType = AnomalyType.CORRELATION_PATTERN,
                    severity = calculateSeverity(anomalyScore),
                    deviceAddresses = listOf(pair.first, pair.second),
                    deviceType = deviceType,
                    anomalyScore = anomalyScore,
                    confidenceLevel = min(correlationCoefficient, 1.0),
                    description = "Two devices consistently appear together (${data.coOccurrences} times, ${String.format("%.0f", correlationCoefficient * 100)}% correlation)",
                    detectionCount = data.coOccurrences,
                    locations = data.locations,
                    timeSpan = if (data.timestamps.isNotEmpty()) {
                        data.timestamps.maxOrNull()!! - data.timestamps.minOrNull()!!
                    } else 0L,
                    firstSeen = data.timestamps.minOrNull() ?: System.currentTimeMillis(),
                    lastSeen = data.timestamps.maxOrNull() ?: System.currentTimeMillis()
                )

                anomalyDetectionDao.insert(anomaly)
            }
        }
    }

    /**
     * Detect sudden appearance of multiple new devices (device clusters)
     */
    private suspend fun analyzeDeviceClusters(deviceType: DeviceType) {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - CLUSTER_ANALYSIS_WINDOW_MS

        val allDetections = deviceDetectionDao.getAllDetectionsInTimeRange(
            deviceType,
            startTime,
            endTime
        )

        if (allDetections.isEmpty()) return

        // Track when each device was first seen
        val deviceFirstSeen = mutableMapOf<String, Long>()
        for (detection in allDetections.sortedBy { it.timestamp }) {
            if (!deviceFirstSeen.containsKey(detection.deviceAddress)) {
                deviceFirstSeen[detection.deviceAddress] = detection.timestamp
            }
        }

        // Group detections by time windows
        val timeWindows = groupDetectionsByTimeWindow(allDetections, CLUSTER_TIME_WINDOW_MS)

        for (window in timeWindows) {
            val windowStart = window.minOfOrNull { it.timestamp } ?: continue
            val windowEnd = window.maxOfOrNull { it.timestamp } ?: continue

            // Find devices that are "new" within this window (first seen recently)
            val newDevicesInWindow = window.filter { detection ->
                val firstSeen = deviceFirstSeen[detection.deviceAddress] ?: return@filter false
                val isNew = firstSeen >= windowStart - NEW_DEVICE_THRESHOLD_MS
                val isNotWhitelisted = !whitelistedDeviceDao.isWhitelisted(detection.deviceAddress)
                isNew && isNotWhitelisted
            }.map { it.deviceAddress }.distinct()

            // If multiple new devices appear at once, it's suspicious
            if (newDevicesInWindow.size >= MIN_DEVICES_FOR_CLUSTER) {
                val anomalyScore = min(newDevicesInWindow.size / 5.0, 1.0)

                if (anomalyScore > ANOMALY_THRESHOLD) {
                    val locationsInWindow = window.mapNotNull { detection ->
                        detection.latitude?.let { lat ->
                            detection.longitude?.let { lon ->
                                LocationPoint(lat, lon, detection.timestamp, detection.accuracy)
                            }
                        }
                    }

                    val anomaly = AnomalyDetection(
                        detectedAt = System.currentTimeMillis(),
                        anomalyType = AnomalyType.NEW_DEVICE_CLUSTER,
                        severity = calculateSeverity(anomalyScore),
                        deviceAddresses = newDevicesInWindow,
                        deviceType = deviceType,
                        anomalyScore = anomalyScore,
                        confidenceLevel = min(anomalyScore * 1.1, 1.0),
                        description = "${newDevicesInWindow.size} new devices appeared simultaneously",
                        detectionCount = newDevicesInWindow.size,
                        locations = locationsInWindow,
                        timeSpan = windowEnd - windowStart,
                        firstSeen = windowStart,
                        lastSeen = windowEnd
                    )

                    anomalyDetectionDao.insert(anomaly)
                }
            }
        }
    }

    /**
     * Group detections into time windows
     */
    private fun groupDetectionsByTimeWindow(
        detections: List<DeviceDetection>,
        windowSizeMs: Long
    ): List<List<DeviceDetection>> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedBy { it.timestamp }
        val windows = mutableListOf<MutableList<DeviceDetection>>()
        var currentWindow = mutableListOf<DeviceDetection>()
        var windowStart = sorted.first().timestamp

        for (detection in sorted) {
            if (detection.timestamp - windowStart > windowSizeMs) {
                if (currentWindow.isNotEmpty()) {
                    windows.add(currentWindow)
                }
                currentWindow = mutableListOf(detection)
                windowStart = detection.timestamp
            } else {
                currentWindow.add(detection)
            }
        }

        if (currentWindow.isNotEmpty()) {
            windows.add(currentWindow)
        }

        return windows
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

    /**
     * Data class for tracking co-occurrence statistics
     */
    private data class CoOccurrenceData(
        val device1: String,
        val device2: String,
        var coOccurrences: Int,
        val timestamps: MutableList<Long>,
        val locations: MutableList<LocationPoint>
    )

    companion object {
        private const val ANALYSIS_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
        private const val MIN_DETECTIONS_FOR_ANALYSIS = 3
        private const val MIN_DETECTIONS_FOR_FREQUENCY_ANALYSIS = 5
        private const val ANOMALY_THRESHOLD = 0.5
        private const val SIGNIFICANT_DISTANCE_METERS = 500.0  // 500 meters
        private const val SUSPICIOUS_FREQUENCY_PER_HOUR = 10.0

        // Correlation analysis constants
        private const val CORRELATION_TIME_WINDOW_MS = 5 * 60 * 1000L  // 5 minutes
        private const val MIN_COOCCURRENCES = 3
        private const val MIN_CORRELATION_COEFFICIENT = 0.5

        // Device cluster analysis constants
        private const val CLUSTER_ANALYSIS_WINDOW_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val CLUSTER_TIME_WINDOW_MS = 10 * 60 * 1000L  // 10 minutes
        private const val NEW_DEVICE_THRESHOLD_MS = 60 * 60 * 1000L  // 1 hour
        private const val MIN_DEVICES_FOR_CLUSTER = 3
    }
}
