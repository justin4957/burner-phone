package com.burnerphone.detector.analysis

import com.burnerphone.detector.data.dao.AnomalyDetectionDao
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.dao.WhitelistedDeviceDao
import com.burnerphone.detector.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

class AnomalyAnalyzerTest {

    private lateinit var analyzer: AnomalyAnalyzer
    private lateinit var mockDeviceDao: FakeDeviceDetectionDao
    private lateinit var mockAnomalyDao: FakeAnomalyDetectionDao
    private lateinit var mockWhitelistDao: FakeWhitelistedDeviceDao

    @Before
    fun setup() {
        mockDeviceDao = FakeDeviceDetectionDao()
        mockAnomalyDao = FakeAnomalyDetectionDao()
        mockWhitelistDao = FakeWhitelistedDeviceDao()
        analyzer = AnomalyAnalyzer(mockDeviceDao, mockAnomalyDao, mockWhitelistDao)
    }

    // ============================================================
    // Distance Calculation Tests (Haversine Formula)
    // ============================================================

    @Test
    fun testCalculateDistance_sameLocation_returnsZero() {
        val lat = 37.7749
        val lon = -122.4194

        val distance = calculateHaversineDistance(lat, lon, lat, lon)

        assertEquals(0.0, distance, 0.1)
    }

    @Test
    fun testCalculateDistance_knownDistance() {
        // San Francisco to Los Angeles (approximately 559 km)
        val sfLat = 37.7749
        val sfLon = -122.4194
        val laLat = 34.0522
        val laLon = -118.2437

        val distance = calculateHaversineDistance(sfLat, sfLon, laLat, laLon)

        // Should be approximately 559,000 meters (559 km)
        assertTrue(distance > 550000.0 && distance < 570000.0)
    }

    @Test
    fun testCalculateDistance_shortDistance() {
        // Two points 500 meters apart (approximate)
        val lat1 = 37.7749
        val lon1 = -122.4194
        val lat2 = 37.7794  // About 500m north
        val lon2 = -122.4194

        val distance = calculateHaversineDistance(lat1, lon1, lat2, lon2)

        // Should be approximately 500 meters
        assertTrue(distance > 450.0 && distance < 550.0)
    }

    @Test
    fun testCalculateDistance_acrossEquator() {
        val lat1 = 10.0
        val lon1 = 0.0
        val lat2 = -10.0
        val lon2 = 0.0

        val distance = calculateHaversineDistance(lat1, lon1, lat2, lon2)

        // Should be approximately 2,222 km
        assertTrue(distance > 2200000.0 && distance < 2250000.0)
    }

    @Test
    fun testCalculateDistance_acrossDateLine() {
        val lat1 = 0.0
        val lon1 = 179.0
        val lat2 = 0.0
        val lon2 = -179.0

        val distance = calculateHaversineDistance(lat1, lon1, lat2, lon2)

        // Should be approximately 222 km (short way around)
        assertTrue(distance > 200000.0 && distance < 250000.0)
    }

    // ============================================================
    // Severity Classification Tests
    // ============================================================

    @Test
    fun testCalculateSeverity_critical() {
        assertEquals(AnomalySeverity.CRITICAL, calculateSeverity(0.8))
        assertEquals(AnomalySeverity.CRITICAL, calculateSeverity(0.9))
        assertEquals(AnomalySeverity.CRITICAL, calculateSeverity(1.0))
    }

    @Test
    fun testCalculateSeverity_high() {
        assertEquals(AnomalySeverity.HIGH, calculateSeverity(0.6))
        assertEquals(AnomalySeverity.HIGH, calculateSeverity(0.7))
        assertEquals(AnomalySeverity.HIGH, calculateSeverity(0.79))
    }

    @Test
    fun testCalculateSeverity_medium() {
        assertEquals(AnomalySeverity.MEDIUM, calculateSeverity(0.4))
        assertEquals(AnomalySeverity.MEDIUM, calculateSeverity(0.5))
        assertEquals(AnomalySeverity.MEDIUM, calculateSeverity(0.59))
    }

    @Test
    fun testCalculateSeverity_low() {
        assertEquals(AnomalySeverity.LOW, calculateSeverity(0.0))
        assertEquals(AnomalySeverity.LOW, calculateSeverity(0.1))
        assertEquals(AnomalySeverity.LOW, calculateSeverity(0.39))
    }

    @Test
    fun testCalculateSeverity_boundaries() {
        assertEquals(AnomalySeverity.CRITICAL, calculateSeverity(0.8))
        assertEquals(AnomalySeverity.HIGH, calculateSeverity(0.6))
        assertEquals(AnomalySeverity.MEDIUM, calculateSeverity(0.4))
        assertEquals(AnomalySeverity.LOW, calculateSeverity(0.39))
    }

    // ============================================================
    // Temporal Clustering Tests
    // ============================================================

    @Test
    fun testAnalyzeAll_noDevices_noAnomalies() = runTest {
        mockDeviceDao.uniqueDevices = emptyList()

        analyzer.analyzeAll()

        assertTrue(mockAnomalyDao.insertedAnomalies.isEmpty())
    }

    @Test
    fun testAnalyzeAll_whitelistedDevice_skipped() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockWhitelistDao.whitelistedAddresses.add(deviceAddress)

        // Create detections that would normally trigger anomaly
        val baseTime = System.currentTimeMillis()
        val detections = List(5) { i ->
            createDetection(deviceAddress, baseTime + (i * 1000L))
        }
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not create any anomalies because device is whitelisted
        assertTrue(mockAnomalyDao.insertedAnomalies.isEmpty())
    }

    @Test
    fun testTemporalClustering_rapidSuccession_detectsAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Create a cluster: 5 detections in rapid succession (1 second apart)
        // Then normal spacing (1 hour apart)
        val detections = mutableListOf<DeviceDetection>()

        // Cluster 1: 5 detections, 1 second apart
        for (i in 0 until 5) {
            detections.add(createDetection(deviceAddress, baseTime + (i * 1000L)))
        }

        // Normal spacing: 3 detections, 1 hour apart
        for (i in 0 until 3) {
            detections.add(createDetection(deviceAddress, baseTime + 3600000L + (i * 3600000L)))
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should detect temporal clustering anomaly
        val anomalies = mockAnomalyDao.insertedAnomalies
        assertTrue(anomalies.isNotEmpty())
        assertEquals(AnomalyType.TEMPORAL_CLUSTERING, anomalies[0].anomalyType)
    }

    @Test
    fun testTemporalClustering_evenlySpaced_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Create evenly spaced detections (1 hour apart)
        val detections = List(10) { i ->
            createDetection(deviceAddress, baseTime + (i * 3600000L))
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not detect temporal clustering (evenly spaced)
        val temporalAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.TEMPORAL_CLUSTERING
        }
        assertTrue(temporalAnomalies.isEmpty())
    }

    @Test
    fun testTemporalClustering_tooFewDetections_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Only 2 detections (minimum is 3)
        val detections = List(2) { i ->
            createDetection(deviceAddress, baseTime + (i * 1000L))
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not analyze with < 3 detections
        assertTrue(mockAnomalyDao.insertedAnomalies.isEmpty())
    }

    @Test
    fun testTemporalClustering_clusterSizeBelowThreshold_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Create a small cluster (only 2 detections close together)
        val detections = mutableListOf(
            createDetection(deviceAddress, baseTime),
            createDetection(deviceAddress, baseTime + 1000L),  // 1 second apart
            createDetection(deviceAddress, baseTime + 3600000L),  // 1 hour later
            createDetection(deviceAddress, baseTime + 7200000L)   // 2 hours later
        )

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not detect anomaly (cluster size < 3)
        assertTrue(mockAnomalyDao.insertedAnomalies.isEmpty())
    }

    // ============================================================
    // Geographic Tracking Tests
    // ============================================================

    @Test
    fun testGeographicTracking_multipleLocations_detectsAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Create detections at different locations > 500m apart
        val detections = listOf(
            createDetection(deviceAddress, baseTime, 37.7749, -122.4194),        // San Francisco
            createDetection(deviceAddress, baseTime + 3600000L, 37.7794, -122.4194),  // 500m north
            createDetection(deviceAddress, baseTime + 7200000L, 37.7849, -122.4194),  // 1km north
            createDetection(deviceAddress, baseTime + 10800000L, 37.7899, -122.4194)  // 1.5km north
        )

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should detect geographic tracking anomaly
        val anomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.GEOGRAPHIC_TRACKING
        }
        assertTrue(anomalies.isNotEmpty())
    }

    @Test
    fun testGeographicTracking_sameLocation_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // All detections at same location
        val detections = List(5) { i ->
            createDetection(deviceAddress, baseTime + (i * 3600000L), 37.7749, -122.4194)
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not detect geographic tracking (same location)
        val geoAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.GEOGRAPHIC_TRACKING
        }
        assertTrue(geoAnomalies.isEmpty())
    }

    @Test
    fun testGeographicTracking_noLocationData_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Detections without location data
        val detections = List(5) { i ->
            createDetection(deviceAddress, baseTime + (i * 3600000L))
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not analyze geographic patterns without location
        val geoAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.GEOGRAPHIC_TRACKING
        }
        assertTrue(geoAnomalies.isEmpty())
    }

    @Test
    fun testGeographicTracking_tooFewLocations_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Only 2 detections with location (minimum is 3)
        val detections = listOf(
            createDetection(deviceAddress, baseTime, 37.7749, -122.4194),
            createDetection(deviceAddress, baseTime + 3600000L, 37.7849, -122.4194)
        )

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not analyze with < 3 locations
        assertTrue(mockAnomalyDao.insertedAnomalies.isEmpty())
    }

    @Test
    fun testGeographicTracking_shortDistances_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Detections at locations < 500m apart
        val detections = listOf(
            createDetection(deviceAddress, baseTime, 37.7749, -122.4194),
            createDetection(deviceAddress, baseTime + 3600000L, 37.7751, -122.4194),  // ~22m north
            createDetection(deviceAddress, baseTime + 7200000L, 37.7753, -122.4194),  // ~44m north
            createDetection(deviceAddress, baseTime + 10800000L, 37.7755, -122.4194)  // ~66m north
        )

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not detect anomaly (distances too small)
        val geoAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.GEOGRAPHIC_TRACKING
        }
        assertTrue(geoAnomalies.isEmpty())
    }

    // ============================================================
    // Frequency Anomaly Tests
    // ============================================================

    @Test
    fun testFrequencyAnomaly_highFrequency_detectsAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // 15 detections in 1 hour = 15 detections/hour (> 10 threshold)
        val detections = List(15) { i ->
            createDetection(deviceAddress, baseTime + (i * 240000L))  // 4 minutes apart
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should detect frequency anomaly
        val anomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.FREQUENCY_ANOMALY
        }
        assertTrue(anomalies.isNotEmpty())
    }

    @Test
    fun testFrequencyAnomaly_normalFrequency_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // 5 detections in 2 hours = 2.5 detections/hour (< 10 threshold)
        val detections = List(5) { i ->
            createDetection(deviceAddress, baseTime + (i * 1440000L))  // 24 minutes apart
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not detect frequency anomaly
        val freqAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.FREQUENCY_ANOMALY
        }
        assertTrue(freqAnomalies.isEmpty())
    }

    @Test
    fun testFrequencyAnomaly_tooFewDetections_noAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Only 4 detections (minimum is 5)
        val detections = List(4) { i ->
            createDetection(deviceAddress, baseTime + (i * 60000L))  // 1 minute apart
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should not analyze with < 5 detections
        assertTrue(mockAnomalyDao.insertedAnomalies.isEmpty())
    }

    @Test
    fun testFrequencyAnomaly_exactlyAtThreshold_detectsAnomaly() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // 10 detections in 1 hour = exactly 10 detections/hour
        val detections = List(10) { i ->
            createDetection(deviceAddress, baseTime + (i * 360000L))  // 6 minutes apart
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should detect anomaly at threshold
        val anomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.FREQUENCY_ANOMALY
        }
        assertTrue(anomalies.isNotEmpty())
    }

    // ============================================================
    // Edge Cases and Integration Tests
    // ============================================================

    @Test
    fun testMultipleAnomalyTypes_sameDevice() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Create detections that trigger both temporal clustering and frequency anomaly
        // 20 detections in 1 hour with a cluster at the beginning
        val detections = mutableListOf<DeviceDetection>()

        // Temporal cluster: 5 detections, 10 seconds apart
        for (i in 0 until 5) {
            detections.add(createDetection(deviceAddress, baseTime + (i * 10000L)))
        }

        // Rest of detections: 15 more in the hour (total 20 = high frequency)
        for (i in 5 until 20) {
            detections.add(createDetection(deviceAddress, baseTime + (i * 180000L)))  // 3 min apart
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Should detect both temporal clustering and frequency anomaly
        val anomalies = mockAnomalyDao.insertedAnomalies
        assertTrue(anomalies.size >= 2)
        assertTrue(anomalies.any { it.anomalyType == AnomalyType.TEMPORAL_CLUSTERING })
        assertTrue(anomalies.any { it.anomalyType == AnomalyType.FREQUENCY_ANOMALY })
    }

    @Test
    fun testAnomalyScoreCapping_doesNotExceedOne() = runTest {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val baseTime = System.currentTimeMillis()

        // Extreme frequency: 100 detections in 1 hour
        val detections = List(100) { i ->
            createDetection(deviceAddress, baseTime + (i * 36000L))
        }

        mockDeviceDao.uniqueDevices = listOf(deviceAddress)
        mockDeviceDao.detectionsForAddress[deviceAddress] = detections

        analyzer.analyzeAll()

        // Anomaly scores should be capped at 1.0
        mockAnomalyDao.insertedAnomalies.forEach { anomaly ->
            assertTrue(anomaly.anomalyScore <= 1.0)
            assertTrue(anomaly.confidenceLevel <= 1.0)
        }
    }

    // ============================================================
    // Correlation Pattern Tests
    // ============================================================

    @Test
    fun testCorrelationPattern_twoDevicesAppearTogether() = runTest {
        val device1 = "AA:BB:CC:DD:EE:01"
        val device2 = "AA:BB:CC:DD:EE:02"
        val baseTime = System.currentTimeMillis()

        // Create detections where both devices appear together multiple times
        val allDetections = mutableListOf<DeviceDetection>()
        for (i in 0..4) {
            val timestamp = baseTime + i * 10 * 60 * 1000  // Every 10 minutes
            // Both devices appear within 2 minutes of each other
            allDetections.add(
                createDetection(device1, timestamp, deviceType = DeviceType.BLUETOOTH_DEVICE)
            )
            allDetections.add(
                createDetection(device2, timestamp + 60 * 1000, deviceType = DeviceType.BLUETOOTH_DEVICE)
            )
        }

        mockDeviceDao.uniqueDevices = listOf(device1, device2)
        mockDeviceDao.allDetectionsByType[DeviceType.BLUETOOTH_DEVICE] = allDetections

        analyzer.analyzeAll()

        // Should detect correlation pattern
        val correlationAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.CORRELATION_PATTERN
        }
        assertTrue(correlationAnomalies.isNotEmpty())

        val anomaly = correlationAnomalies.first()
        assertEquals(2, anomaly.deviceAddresses.size)
        assertTrue(anomaly.deviceAddresses.contains(device1))
        assertTrue(anomaly.deviceAddresses.contains(device2))
        assertTrue(anomaly.anomalyScore > 0.0)
    }

    @Test
    fun testCorrelationPattern_noCorrelation() = runTest {
        val device1 = "AA:BB:CC:DD:EE:01"
        val device2 = "AA:BB:CC:DD:EE:02"
        val baseTime = System.currentTimeMillis()

        // Create detections where devices appear independently (different time windows)
        val allDetections = mutableListOf<DeviceDetection>()
        for (i in 0..4) {
            // Device 1 appears
            allDetections.add(
                createDetection(device1, baseTime + i * 30 * 60 * 1000, deviceType = DeviceType.BLUETOOTH_DEVICE)
            )
            // Device 2 appears 20 minutes later (outside 5-minute window)
            allDetections.add(
                createDetection(device2, baseTime + i * 30 * 60 * 1000 + 20 * 60 * 1000, deviceType = DeviceType.BLUETOOTH_DEVICE)
            )
        }

        mockDeviceDao.uniqueDevices = listOf(device1, device2)
        mockDeviceDao.allDetectionsByType[DeviceType.BLUETOOTH_DEVICE] = allDetections

        analyzer.analyzeAll()

        // Should not detect correlation pattern
        val correlationAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.CORRELATION_PATTERN
        }
        assertEquals(0, correlationAnomalies.size)
    }

    @Test
    fun testCorrelationPattern_whitelistedDeviceExcluded() = runTest {
        val device1 = "AA:BB:CC:DD:EE:01"
        val device2 = "AA:BB:CC:DD:EE:02"
        val baseTime = System.currentTimeMillis()

        // Whitelist device1
        mockWhitelistDao.whitelistedAddresses.add(device1)

        // Create detections where both devices appear together
        val allDetections = mutableListOf<DeviceDetection>()
        for (i in 0..4) {
            val timestamp = baseTime + i * 10 * 60 * 1000
            allDetections.add(
                createDetection(device1, timestamp, deviceType = DeviceType.BLUETOOTH_DEVICE)
            )
            allDetections.add(
                createDetection(device2, timestamp + 60 * 1000, deviceType = DeviceType.BLUETOOTH_DEVICE)
            )
        }

        mockDeviceDao.uniqueDevices = listOf(device1, device2)
        mockDeviceDao.allDetectionsByType[DeviceType.BLUETOOTH_DEVICE] = allDetections

        analyzer.analyzeAll()

        // Should not detect correlation (one device whitelisted)
        val correlationAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.CORRELATION_PATTERN
        }
        assertEquals(0, correlationAnomalies.size)
    }

    // ============================================================
    // Device Cluster Tests
    // ============================================================

    @Test
    fun testDeviceCluster_multipleNewDevices() = runTest {
        val newDevices = listOf(
            "AA:BB:CC:DD:EE:01",
            "AA:BB:CC:DD:EE:02",
            "AA:BB:CC:DD:EE:03",
            "AA:BB:CC:DD:EE:04"
        )
        val baseTime = System.currentTimeMillis()

        // All devices appear for the first time within a short window
        val allDetections = mutableListOf<DeviceDetection>()
        val clusterTime = baseTime
        for ((index, device) in newDevices.withIndex()) {
            allDetections.add(
                createDetection(device, clusterTime + index * 60 * 1000, deviceType = DeviceType.WIFI_NETWORK)
            )
        }

        mockDeviceDao.uniqueDevices = newDevices
        mockDeviceDao.allDetectionsByType[DeviceType.WIFI_NETWORK] = allDetections

        analyzer.analyzeAll()

        // Should detect device cluster
        val clusterAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.NEW_DEVICE_CLUSTER
        }
        assertTrue(clusterAnomalies.isNotEmpty())

        val anomaly = clusterAnomalies.first()
        assertTrue(anomaly.deviceAddresses.size >= 3)
        assertTrue(anomaly.anomalyScore > 0.0)
    }

    @Test
    fun testDeviceCluster_onlyTwoDevices() = runTest {
        val newDevices = listOf(
            "AA:BB:CC:DD:EE:01",
            "AA:BB:CC:DD:EE:02"
        )
        val baseTime = System.currentTimeMillis()

        // Only two devices appear (below threshold of 3)
        val allDetections = mutableListOf<DeviceDetection>()
        for ((index, device) in newDevices.withIndex()) {
            allDetections.add(
                createDetection(device, baseTime + index * 60 * 1000, deviceType = DeviceType.WIFI_NETWORK)
            )
        }

        mockDeviceDao.uniqueDevices = newDevices
        mockDeviceDao.allDetectionsByType[DeviceType.WIFI_NETWORK] = allDetections

        analyzer.analyzeAll()

        // Should not detect cluster (below threshold)
        val clusterAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.NEW_DEVICE_CLUSTER
        }
        assertEquals(0, clusterAnomalies.size)
    }

    @Test
    fun testDeviceCluster_devicesNotNew() = runTest {
        val devices = listOf(
            "AA:BB:CC:DD:EE:01",
            "AA:BB:CC:DD:EE:02",
            "AA:BB:CC:DD:EE:03"
        )
        val baseTime = System.currentTimeMillis()

        // Devices appear long before the cluster window (2 hours ago)
        val allDetections = mutableListOf<DeviceDetection>()
        val oldTime = baseTime - 2 * 60 * 60 * 1000
        for (device in devices) {
            // First appearance (old)
            allDetections.add(
                createDetection(device, oldTime, deviceType = DeviceType.WIFI_NETWORK)
            )
            // Recent appearance
            allDetections.add(
                createDetection(device, baseTime, deviceType = DeviceType.WIFI_NETWORK)
            )
        }

        mockDeviceDao.uniqueDevices = devices
        mockDeviceDao.allDetectionsByType[DeviceType.WIFI_NETWORK] = allDetections

        analyzer.analyzeAll()

        // Should not detect cluster (devices are not new)
        val clusterAnomalies = mockAnomalyDao.insertedAnomalies.filter {
            it.anomalyType == AnomalyType.NEW_DEVICE_CLUSTER
        }
        assertEquals(0, clusterAnomalies.size)
    }

    // ============================================================
    // Helper Functions
    // ============================================================

    private fun createDetection(
        address: String,
        timestamp: Long,
        latitude: Double? = null,
        longitude: Double? = null,
        deviceType: DeviceType = DeviceType.WIFI_NETWORK
    ): DeviceDetection {
        return DeviceDetection(
            deviceAddress = address,
            deviceType = deviceType,
            deviceName = "Test Device",
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            accuracy = if (latitude != null && longitude != null) 10f else null,
            signalStrength = -50
        )
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun calculateSeverity(anomalyScore: Double): AnomalySeverity {
        return when {
            anomalyScore >= 0.8 -> AnomalySeverity.CRITICAL
            anomalyScore >= 0.6 -> AnomalySeverity.HIGH
            anomalyScore >= 0.4 -> AnomalySeverity.MEDIUM
            else -> AnomalySeverity.LOW
        }
    }

    // ============================================================
    // Fake/Mock DAOs
    // ============================================================

    class FakeDeviceDetectionDao : DeviceDetectionDao {
        var uniqueDevices = listOf<String>()
        val detectionsForAddress = mutableMapOf<String, List<DeviceDetection>>()
        val allDetectionsByType = mutableMapOf<DeviceType, List<DeviceDetection>>()

        override suspend fun insert(detection: DeviceDetection): Long = 0
        override suspend fun insertAll(detections: List<DeviceDetection>) {}
        override suspend fun update(detection: DeviceDetection) {}
        override suspend fun delete(detection: DeviceDetection) {}
        override fun getAllDetections(): Flow<List<DeviceDetection>> = flowOf(emptyList())
        override fun getDetectionsByType(type: DeviceType): Flow<List<DeviceDetection>> = flowOf(emptyList())
        override fun getDetectionsByAddress(address: String): Flow<List<DeviceDetection>> = flowOf(emptyList())

        override suspend fun getDetectionsInTimeRange(
            address: String,
            startTime: Long,
            endTime: Long
        ): List<DeviceDetection> {
            return detectionsForAddress[address] ?: emptyList()
        }

        override suspend fun getAllDetectionsInTimeRange(
            type: DeviceType,
            startTime: Long,
            endTime: Long
        ): List<DeviceDetection> {
            return allDetectionsByType[type] ?: emptyList()
        }

        override suspend fun getUniqueDeviceAddresses(type: DeviceType): List<String> {
            return uniqueDevices
        }

        override suspend fun getUniqueDeviceCount(type: DeviceType): Int = uniqueDevices.size
        override suspend fun deleteOldDetections(cutoffTime: Long): Int = 0
        override suspend fun getDetectionsWithLocation(startTime: Long): List<DeviceDetection> = emptyList()
        override fun getRecentDetections(startTime: Long): Flow<List<DeviceDetection>> = flowOf(emptyList())
    }

    class FakeAnomalyDetectionDao : AnomalyDetectionDao {
        val insertedAnomalies = mutableListOf<AnomalyDetection>()

        override suspend fun insert(anomaly: AnomalyDetection): Long {
            insertedAnomalies.add(anomaly)
            return insertedAnomalies.size.toLong()
        }

        override suspend fun update(anomaly: AnomalyDetection) {}
        override suspend fun delete(anomaly: AnomalyDetection) {}
        override fun getAllAnomalies(): Flow<List<AnomalyDetection>> = flowOf(emptyList())
        override fun getUnacknowledgedAnomalies(): Flow<List<AnomalyDetection>> = flowOf(emptyList())
        override fun getAnomaliesByType(type: AnomalyType): Flow<List<AnomalyDetection>> = flowOf(emptyList())
        override fun getAnomaliesBySeverity(severity: AnomalySeverity): Flow<List<AnomalyDetection>> = flowOf(emptyList())
        override fun getUnacknowledgedCount(): Flow<Int> = flowOf(0)
        override suspend fun deleteOldAnomalies(cutoffTime: Long): Int = 0
    }

    class FakeWhitelistedDeviceDao : WhitelistedDeviceDao {
        val whitelistedAddresses = mutableSetOf<String>()

        override suspend fun insert(device: WhitelistedDevice) {
            whitelistedAddresses.add(device.deviceAddress)
        }

        override suspend fun delete(device: WhitelistedDevice) {
            whitelistedAddresses.remove(device.deviceAddress)
        }

        override suspend fun deleteByAddress(deviceAddress: String) {
            whitelistedAddresses.remove(deviceAddress)
        }

        override fun getAllWhitelisted(): Flow<List<WhitelistedDevice>> = flowOf(emptyList())
        override suspend fun getByAddress(deviceAddress: String): WhitelistedDevice? = null

        override suspend fun isWhitelisted(deviceAddress: String): Boolean {
            return whitelistedAddresses.contains(deviceAddress)
        }

        override fun isWhitelistedFlow(deviceAddress: String): Flow<Boolean> = flowOf(false)
        override fun getWhitelistedCount(): Flow<Int> = flowOf(whitelistedAddresses.size)
    }
}
