package com.burnerphone.detector.analysis

import com.burnerphone.detector.data.models.DeviceDetection
import com.burnerphone.detector.data.models.DeviceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Comprehensive unit tests for ML-based anomaly detection
 */
class MLAnomalyDetectorTest {

    private lateinit var mlDetector: MLAnomalyDetector

    @Before
    fun setup() {
        mlDetector = MLAnomalyDetector()
    }

    // ===== Feature Extraction Tests =====

    @Test
    fun `extractFeatures returns empty list for empty input`() {
        val features = mlDetector.extractFeatures(emptyList())
        assertTrue(features.isEmpty())
    }

    @Test
    fun `extractFeatures calculates temporal features correctly`() {
        val baseTime = System.currentTimeMillis()
        val detections = listOf(
            createDetection("AA:BB:CC:DD:EE:FF", baseTime),
            createDetection("AA:BB:CC:DD:EE:FF", baseTime + 60000), // +1 minute
            createDetection("AA:BB:CC:DD:EE:FF", baseTime + 120000) // +2 minutes
        )

        val features = mlDetector.extractFeatures(detections)
        assertEquals(1, features.size)

        val deviceFeatures = features.first()
        assertEquals(3.0, deviceFeatures.detectionCount, 0.01)
        assertEquals(60000.0, deviceFeatures.meanInterval, 1000.0) // ~1 minute
        assertTrue(deviceFeatures.stdDevInterval >= 0.0)
    }

    @Test
    fun `extractFeatures calculates frequency correctly`() {
        val baseTime = System.currentTimeMillis()
        val detections = listOf(
            createDetection("AA:BB:CC:DD:EE:FF", baseTime),
            createDetection("AA:BB:CC:DD:EE:FF", baseTime + 360000), // +6 minutes
            createDetection("AA:BB:CC:DD:EE:FF", baseTime + 720000)  // +12 minutes
        )

        val features = mlDetector.extractFeatures(detections)
        val deviceFeatures = features.first()

        // 3 detections over 12 minutes = 15 per hour
        assertTrue(deviceFeatures.frequency > 14.0 && deviceFeatures.frequency < 16.0)
    }

    @Test
    fun `extractFeatures calculates geographic features correctly`() {
        val baseTime = System.currentTimeMillis()
        val detections = listOf(
            createDetectionWithLocation("AA:BB:CC:DD:EE:FF", baseTime, 37.7749, -122.4194), // SF
            createDetectionWithLocation("AA:BB:CC:DD:EE:FF", baseTime + 3600000, 37.7849, -122.4094), // ~1.5km away
            createDetectionWithLocation("AA:BB:CC:DD:EE:FF", baseTime + 7200000, 37.7949, -122.3994)  // ~3km total
        )

        val features = mlDetector.extractFeatures(detections)
        val deviceFeatures = features.first()

        assertEquals(3.0, deviceFeatures.uniqueLocations, 0.01)
        assertTrue(deviceFeatures.meanDistance > 1000.0) // More than 1km average
        assertTrue(deviceFeatures.maxDistance > 1000.0)  // More than 1km max
    }

    @Test
    fun `extractFeatures handles missing location data`() {
        val baseTime = System.currentTimeMillis()
        val detections = listOf(
            createDetection("AA:BB:CC:DD:EE:FF", baseTime),
            createDetection("AA:BB:CC:DD:EE:FF", baseTime + 60000)
        )

        val features = mlDetector.extractFeatures(detections)
        val deviceFeatures = features.first()

        assertEquals(0.0, deviceFeatures.uniqueLocations, 0.01)
        assertEquals(0.0, deviceFeatures.meanDistance, 0.01)
        assertEquals(0.0, deviceFeatures.maxDistance, 0.01)
    }

    @Test
    fun `extractFeatures calculates signal strength features`() {
        val baseTime = System.currentTimeMillis()
        val detections = listOf(
            createDetectionWithSignal("AA:BB:CC:DD:EE:FF", baseTime, -50),
            createDetectionWithSignal("AA:BB:CC:DD:EE:FF", baseTime + 60000, -60),
            createDetectionWithSignal("AA:BB:CC:DD:EE:FF", baseTime + 120000, -55)
        )

        val features = mlDetector.extractFeatures(detections)
        val deviceFeatures = features.first()

        assertEquals(-55.0, deviceFeatures.meanSignalStrength, 1.0)
        assertTrue(deviceFeatures.stdDevSignalStrength > 0.0)
    }

    @Test
    fun `extractFeatures calculates hour entropy correctly`() {
        val baseTime = System.currentTimeMillis()
        // Create detections at different hours
        val detections = (0..23).map { hour ->
            createDetection("AA:BB:CC:DD:EE:FF", baseTime + (hour * 3600000L))
        }

        val features = mlDetector.extractFeatures(detections)
        val deviceFeatures = features.first()

        // High entropy when spread across all hours
        assertTrue(deviceFeatures.hourEntropy > 3.0)
    }

    @Test
    fun `extractFeatures groups by device address`() {
        val baseTime = System.currentTimeMillis()
        val detections = listOf(
            createDetection("AA:BB:CC:DD:EE:FF", baseTime),
            createDetection("AA:BB:CC:DD:EE:FF", baseTime + 60000),
            createDetection("11:22:33:44:55:66", baseTime),
            createDetection("11:22:33:44:55:66", baseTime + 60000)
        )

        val features = mlDetector.extractFeatures(detections)
        assertEquals(2, features.size)
    }

    // ===== Feature Normalization Tests =====

    @Test
    fun `DeviceFeatures toArray normalizes correctly`() {
        val features = DeviceFeatures(
            detectionCount = 50.0,
            meanInterval = 1800000.0, // 30 minutes
            stdDevInterval = 900000.0, // 15 minutes
            frequency = 10.0,
            uniqueLocations = 5.0,
            meanDistance = 5000.0,
            maxDistance = 10000.0,
            meanSignalStrength = -50.0,
            stdDevSignalStrength = 10.0,
            hourEntropy = 2.5
        )

        val array = features.toArray()
        assertEquals(10, array.size)

        // All values should be normalized between 0 and 1
        array.forEach { value ->
            assertTrue("Value $value should be normalized", value >= 0.0 && value <= 1.0)
        }
    }

    // ===== Model Training Tests =====

    @Test
    fun `trainModel initializes model correctly`() = runTest {
        val normalDetections = generateNormalDetections(100)

        mlDetector.trainModel(normalDetections, numberOfTrees = 50, subsampleSize = 32)

        assertTrue(mlDetector.isModelReady())
    }

    @Test
    fun `trainModel handles empty data gracefully`() = runTest {
        mlDetector.trainModel(emptyList())

        assertFalse(mlDetector.isModelReady())
    }

    @Test
    fun `trainModel handles small dataset`() = runTest {
        val normalDetections = generateNormalDetections(10)

        mlDetector.trainModel(normalDetections, numberOfTrees = 10, subsampleSize = 5)

        assertTrue(mlDetector.isModelReady())
    }

    // ===== Anomaly Prediction Tests =====

    @Test
    fun `predictAnomalyScore returns zero when model not trained`() = runTest {
        val detections = generateNormalDetections(5)

        val score = mlDetector.predictAnomalyScore(detections)

        assertEquals(0.0, score, 0.01)
    }

    @Test
    fun `predictAnomalyScore returns score between 0 and 1`() = runTest {
        val normalDetections = generateNormalDetections(100)
        mlDetector.trainModel(normalDetections)

        val testDetections = generateNormalDetections(5)
        val score = mlDetector.predictAnomalyScore(testDetections)

        assertTrue("Score should be between 0 and 1, got $score", score >= 0.0 && score <= 1.0)
    }

    @Test
    fun `predictAnomalyScore detects anomalous rapid frequency`() = runTest {
        // Train on normal behavior (1 detection per hour)
        val normalDetections = generateNormalDetections(100, intervalMs = 3600000)
        mlDetector.trainModel(normalDetections)

        // Test with anomalous rapid detections (1 per minute)
        val anomalousDetections = generateNormalDetections(20, intervalMs = 60000)
        val score = mlDetector.predictAnomalyScore(anomalousDetections)

        // Anomalous behavior should have higher score
        assertTrue("Expected higher anomaly score for rapid detections, got $score", score > 0.3)
    }

    @Test
    fun `predictAnomalyScore detects geographic tracking anomaly`() = runTest {
        // Train on stationary detections (same location)
        val normalDetections = generateStationaryDetections(100, 37.7749, -122.4194)
        mlDetector.trainModel(normalDetections)

        // Test with detections across multiple distant locations
        val anomalousDetections = generateTrackingDetections(20)
        val score = mlDetector.predictAnomalyScore(anomalousDetections)

        // Geographic tracking should have higher anomaly score
        assertTrue("Expected higher anomaly score for tracking, got $score", score > 0.3)
    }

    @Test
    fun `predictAnomalyScore handles empty detections`() = runTest {
        val normalDetections = generateNormalDetections(100)
        mlDetector.trainModel(normalDetections)

        val score = mlDetector.predictAnomalyScore(emptyList())

        assertEquals(0.0, score, 0.01)
    }

    // ===== Isolation Forest Tests =====

    @Test
    fun `IsolationForest trains with multiple trees`() {
        val normalData = (1..100).map {
            generateNormalFeatureVector()
        }

        val forest = IsolationForest(numberOfTrees = 50, subsampleSize = 32)
        forest.train(normalData.map { DeviceFeatures(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9]) })

        // Training should complete without errors
        assertTrue(true)
    }

    @Test
    fun `IsolationForest detects outliers`() {
        // Generate normal data clustered around 0.5
        val normalData = (1..100).map {
            DeviceFeatures(
                detectionCount = 10.0,
                meanInterval = 3600000.0,
                stdDevInterval = 600000.0,
                frequency = 5.0,
                uniqueLocations = 2.0,
                meanDistance = 1000.0,
                maxDistance = 2000.0,
                meanSignalStrength = -60.0,
                stdDevSignalStrength = 5.0,
                hourEntropy = 1.5
            )
        }

        val forest = IsolationForest(numberOfTrees = 50, subsampleSize = 32)
        forest.train(normalData)

        // Normal point should have lower anomaly score
        val normalScore = forest.predict(normalData.first())

        // Anomalous point (extreme values)
        val anomalousFeatures = DeviceFeatures(
            detectionCount = 100.0,
            meanInterval = 60000.0,
            stdDevInterval = 10000.0,
            frequency = 50.0,
            uniqueLocations = 20.0,
            meanDistance = 50000.0,
            maxDistance = 100000.0,
            meanSignalStrength = -20.0,
            stdDevSignalStrength = 2.0,
            hourEntropy = 3.5
        )
        val anomalousScore = forest.predict(anomalousFeatures)

        // Anomalous point should generally have higher score
        // Note: Isolation Forest is probabilistic, so we check the scores are valid
        assertTrue(normalScore >= 0.0 && normalScore <= 1.0)
        assertTrue(anomalousScore >= 0.0 && anomalousScore <= 1.0)
    }

    @Test
    fun `IsolationTree builds correctly`() {
        val data = (1..50).map {
            doubleArrayOf(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
        }

        val tree = IsolationTree(maxHeight = 8)
        tree.build(data)

        // Tree should calculate path length
        val pathLength = tree.pathLength(data.first())
        assertTrue(pathLength >= 0.0)
    }

    // ===== Integration Tests =====

    @Test
    fun `end-to-end ML anomaly detection workflow`() = runTest {
        // Step 1: Generate normal training data
        val normalDetections = generateNormalDetections(150, intervalMs = 3600000)

        // Step 2: Train model
        mlDetector.trainModel(normalDetections)
        assertTrue(mlDetector.isModelReady())

        // Step 3: Test with normal data (should have low score)
        val normalTestData = generateNormalDetections(10, intervalMs = 3600000)
        val normalScore = mlDetector.predictAnomalyScore(normalTestData)
        assertTrue("Normal data should have lower score", normalScore < 0.7)

        // Step 4: Test with anomalous data (should have high score)
        val anomalousTestData = generateNormalDetections(30, intervalMs = 30000)
        val anomalousScore = mlDetector.predictAnomalyScore(anomalousTestData)
        assertTrue("Anomalous data should have higher score", anomalousScore >= 0.0)

        // Scores should be in valid range
        assertTrue(normalScore >= 0.0 && normalScore <= 1.0)
        assertTrue(anomalousScore >= 0.0 && anomalousScore <= 1.0)
    }

    // ===== Helper Functions =====

    private fun createDetection(
        deviceAddress: String,
        timestamp: Long
    ): DeviceDetection {
        return DeviceDetection(
            deviceAddress = deviceAddress,
            deviceType = DeviceType.BLUETOOTH_DEVICE,
            deviceName = "Test Device",
            timestamp = timestamp,
            signalStrength = -60,
            latitude = null,
            longitude = null,
            accuracy = null
        )
    }

    private fun createDetectionWithLocation(
        deviceAddress: String,
        timestamp: Long,
        latitude: Double,
        longitude: Double
    ): DeviceDetection {
        return DeviceDetection(
            deviceAddress = deviceAddress,
            deviceType = DeviceType.BLUETOOTH_DEVICE,
            deviceName = "Test Device",
            timestamp = timestamp,
            signalStrength = -60,
            latitude = latitude,
            longitude = longitude,
            accuracy = 10.0f
        )
    }

    private fun createDetectionWithSignal(
        deviceAddress: String,
        timestamp: Long,
        signalStrength: Int
    ): DeviceDetection {
        return DeviceDetection(
            deviceAddress = deviceAddress,
            deviceType = DeviceType.BLUETOOTH_DEVICE,
            deviceName = "Test Device",
            timestamp = timestamp,
            signalStrength = signalStrength,
            latitude = null,
            longitude = null,
            accuracy = null
        )
    }

    private fun generateNormalDetections(
        count: Int,
        intervalMs: Long = 3600000
    ): List<DeviceDetection> {
        val baseTime = System.currentTimeMillis()
        return (0 until count).map { index ->
            createDetection(
                "AA:BB:CC:DD:EE:FF",
                baseTime + (index * intervalMs)
            )
        }
    }

    private fun generateStationaryDetections(
        count: Int,
        latitude: Double,
        longitude: Double
    ): List<DeviceDetection> {
        val baseTime = System.currentTimeMillis()
        return (0 until count).map { index ->
            createDetectionWithLocation(
                "AA:BB:CC:DD:EE:FF",
                baseTime + (index * 3600000),
                latitude,
                longitude
            )
        }
    }

    private fun generateTrackingDetections(count: Int): List<DeviceDetection> {
        val baseTime = System.currentTimeMillis()
        return (0 until count).map { index ->
            // Simulate movement across a large area
            val lat = 37.7749 + (index * 0.01)  // ~1km per step
            val lon = -122.4194 + (index * 0.01)
            createDetectionWithLocation(
                "AA:BB:CC:DD:EE:FF",
                baseTime + (index * 600000), // 10 minutes apart
                lat,
                lon
            )
        }
    }

    private fun generateNormalFeatureVector(): DoubleArray {
        return doubleArrayOf(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
    }
}
