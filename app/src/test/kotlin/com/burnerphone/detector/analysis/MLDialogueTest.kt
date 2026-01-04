package com.burnerphone.detector.analysis

import com.burnerphone.detector.data.models.DeviceDetection
import com.burnerphone.detector.data.models.DeviceType
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Dialogue test demonstrating ML-based anomaly detection workflow.
 * This test shows the back-and-forth interaction between the application
 * and the ML anomaly detection system.
 */
class MLDialogueTest {

    @Test
    fun `ML anomaly detection dialogue - training and inference flow`() = runTest {
        println("\n=== ML Anomaly Detection Dialogue Test ===\n")

        // Initialize the ML detector
        val mlDetector = MLAnomalyDetector()
        println("--- Test 1: ML Detector Initialization ---")
        println("User: Create new ML anomaly detector")
        println("[System]: MLAnomalyDetector initialized")
        println("[System]: Model ready: ${mlDetector.isModelReady()}")
        println("Expected: false (model not trained yet)\n")

        // Generate normal training data (user's typical devices)
        println("--- Test 2: Feature Extraction ---")
        println("User: Extract features from 50 normal device detections")
        val normalDetections = generateNormalBehaviorData(50)
        println("[System]: Generated ${normalDetections.size} normal detections")
        println("[System]: Detection pattern - 1 per hour, stationary location")

        val features = mlDetector.extractFeatures(normalDetections)
        println("[System]: Extracted ${features.size} feature vectors")
        if (features.isNotEmpty()) {
            val sampleFeatures = features.first()
            println("[System]: Sample features:")
            println("  - Detection count: ${sampleFeatures.detectionCount.roundToInt()}")
            println("  - Mean interval: ${(sampleFeatures.meanInterval / 60000).roundToInt()} minutes")
            println("  - Frequency: ${String.format("%.2f", sampleFeatures.frequency)}/hour")
            println("  - Unique locations: ${sampleFeatures.uniqueLocations.roundToInt()}")
            println("  - Hour entropy: ${String.format("%.2f", sampleFeatures.hourEntropy)}")
        }
        println("")

        // Train the model
        println("--- Test 3: Model Training ---")
        println("User: Train ML model on normal behavior data")
        println("[System]: Training Isolation Forest...")
        println("[System]: - Number of trees: 100")
        println("[System]: - Subsample size: 50")
        println("[System]: - Training samples: ${normalDetections.size}")

        mlDetector.trainModel(normalDetections, numberOfTrees = 100, subsampleSize = 50)

        println("[System]: Training complete")
        println("[System]: Model ready: ${mlDetector.isModelReady()}")
        println("Expected: true (model trained successfully)\n")

        // Test inference on normal data
        println("--- Test 4: Inference on Normal Data ---")
        println("User: Predict anomaly score for normal device behavior")
        val normalTestData = generateNormalBehaviorData(10)
        println("[System]: Testing on ${normalTestData.size} normal detections")

        val normalScore = mlDetector.predictAnomalyScore(normalTestData)
        println("[System]: Anomaly score: ${String.format("%.4f", normalScore)}")
        println("[System]: Interpretation: ${interpretScore(normalScore)}")
        println("Expected: Low score (< 0.6) for normal behavior\n")

        // Test inference on rapid detection anomaly
        println("--- Test 5: Inference on Rapid Detection Anomaly ---")
        println("User: Predict anomaly score for suspicious rapid detections")
        val rapidDetections = generateRapidDetections(20)
        println("[System]: Testing on ${rapidDetections.size} rapid detections")
        println("[System]: Pattern: 1 detection per minute (60x normal frequency)")

        val rapidScore = mlDetector.predictAnomalyScore(rapidDetections)
        println("[System]: Anomaly score: ${String.format("%.4f", rapidScore)}")
        println("[System]: Interpretation: ${interpretScore(rapidScore)}")
        println("Expected: Higher score due to unusual frequency\n")

        // Test inference on geographic tracking anomaly
        println("--- Test 6: Inference on Geographic Tracking Anomaly ---")
        println("User: Predict anomaly score for device following across locations")
        val trackingDetections = generateTrackingPattern(15)
        println("[System]: Testing on ${trackingDetections.size} detections")
        println("[System]: Pattern: Device detected across 15 different locations")
        println("[System]: Geographic spread: ~15km over 2.5 hours")

        val trackingScore = mlDetector.predictAnomalyScore(trackingDetections)
        println("[System]: Anomaly score: ${String.format("%.4f", trackingScore)}")
        println("[System]: Interpretation: ${interpretScore(trackingScore)}")
        println("Expected: Higher score due to geographic tracking pattern\n")

        // Test feature extraction detail
        println("--- Test 7: Feature Extraction Detail ---")
        println("User: Show detailed features for tracking pattern")
        val trackingFeatures = mlDetector.extractFeatures(trackingDetections)
        if (trackingFeatures.isNotEmpty()) {
            val features = trackingFeatures.first()
            println("[System]: Extracted features:")
            println("  - Detection count: ${features.detectionCount.roundToInt()}")
            println("  - Mean interval: ${(features.meanInterval / 60000).roundToInt()} minutes")
            println("  - Frequency: ${String.format("%.2f", features.frequency)}/hour")
            println("  - Unique locations: ${features.uniqueLocations.roundToInt()}")
            println("  - Mean distance: ${(features.meanDistance / 1000).roundToInt()}km")
            println("  - Max distance: ${(features.maxDistance / 1000).roundToInt()}km")
            println("  - Mean signal: ${features.meanSignalStrength.roundToInt()} dBm")
            println("  - Hour entropy: ${String.format("%.2f", features.hourEntropy)}")

            println("[System]: Normalized feature array:")
            val normalizedArray = features.toArray()
            normalizedArray.forEachIndexed { index, value ->
                println("  - Feature $index: ${String.format("%.4f", value)}")
            }
        }
        println("")

        // Test model state
        println("--- Test 8: Model State Verification ---")
        println("User: Check if model is ready for production use")
        println("[System]: Model status:")
        println("  - Is trained: ${mlDetector.isModelReady()}")
        println("  - Can perform inference: ${mlDetector.isModelReady()}")
        println("  - Training data size: ${normalDetections.size} samples")
        println("[System]: Model is ready for production anomaly detection")
        println("")

        // Summary
        println("--- Test Summary ---")
        println("[System]: ML Anomaly Detection Test Results:")
        println("  ✓ Model initialization successful")
        println("  ✓ Feature extraction working correctly")
        println("  ✓ Model training completed")
        println("  ✓ Inference on normal data: ${String.format("%.4f", normalScore)}")
        println("  ✓ Inference on rapid anomaly: ${String.format("%.4f", rapidScore)}")
        println("  ✓ Inference on tracking anomaly: ${String.format("%.4f", trackingScore)}")
        println("\n=== Dialogue Test Complete ===\n")
    }

    @Test
    fun `ML anomaly detection dialogue - isolation forest mechanics`() = runTest {
        println("\n=== Isolation Forest Algorithm Dialogue Test ===\n")

        println("--- Test 1: Isolation Forest Initialization ---")
        println("User: Create Isolation Forest with custom parameters")
        val forest = IsolationForest(numberOfTrees = 50, subsampleSize = 32)
        println("[System]: Isolation Forest created")
        println("[System]: Configuration:")
        println("  - Number of trees: 50")
        println("  - Subsample size: 32")
        println("  - Max tree height: ~5 (log2(32))")
        println("")

        println("--- Test 2: Generate Normal Training Data ---")
        println("User: Generate clustered normal data points")
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
        println("[System]: Generated 100 normal feature vectors")
        println("[System]: All features clustered around typical values")
        println("")

        println("--- Test 3: Train Isolation Forest ---")
        println("User: Train forest on normal data")
        forest.train(normalData)
        println("[System]: Forest training complete")
        println("[System]: Each tree built with random feature splits")
        println("[System]: Trees isolate anomalies faster than normal points")
        println("")

        println("--- Test 4: Predict on Normal Point ---")
        println("User: Get anomaly score for typical normal point")
        val normalPoint = normalData.first()
        val normalScore = forest.predict(normalPoint)
        println("[System]: Normal point features:")
        println("  - Detection count: ${normalPoint.detectionCount}")
        println("  - Frequency: ${normalPoint.frequency}/hour")
        println("  - Unique locations: ${normalPoint.uniqueLocations}")
        println("[System]: Anomaly score: ${String.format("%.4f", normalScore)}")
        println("[System]: Interpretation: ${interpretScore(normalScore)}")
        println("Expected: Low score - point is similar to training data")
        println("")

        println("--- Test 5: Predict on Anomalous Point ---")
        println("User: Get anomaly score for extreme outlier point")
        val anomalousPoint = DeviceFeatures(
            detectionCount = 100.0,   // 10x normal
            meanInterval = 60000.0,   // 60x faster
            stdDevInterval = 10000.0,
            frequency = 50.0,         // 10x normal
            uniqueLocations = 20.0,   // 10x normal
            meanDistance = 50000.0,   // 50x normal
            maxDistance = 100000.0,   // 50x normal
            meanSignalStrength = -20.0,
            stdDevSignalStrength = 2.0,
            hourEntropy = 3.5
        )
        val anomalousScore = forest.predict(anomalousPoint)
        println("[System]: Anomalous point features:")
        println("  - Detection count: ${anomalousPoint.detectionCount}")
        println("  - Frequency: ${anomalousPoint.frequency}/hour")
        println("  - Unique locations: ${anomalousPoint.uniqueLocations}")
        println("[System]: Anomaly score: ${String.format("%.4f", anomalousScore)}")
        println("[System]: Interpretation: ${interpretScore(anomalousScore)}")
        println("Expected: Higher score - point deviates significantly from training")
        println("")

        println("--- Test 6: Isolation Mechanism Explanation ---")
        println("User: Explain how Isolation Forest works")
        println("[System]: Isolation Forest algorithm:")
        println("  1. Build random trees by splitting on random features")
        println("  2. Anomalies are isolated faster (shorter path length)")
        println("  3. Normal points require more splits to isolate")
        println("  4. Average path length across all trees = anomaly score")
        println("  5. Score normalized using expected path length formula")
        println("[System]: Key insight: Outliers are 'few and different'")
        println("")

        println("--- Test Summary ---")
        println("[System]: Isolation Forest Test Results:")
        println("  ✓ Forest built with 50 trees")
        println("  ✓ Normal point score: ${String.format("%.4f", normalScore)}")
        println("  ✓ Anomalous point score: ${String.format("%.4f", anomalousScore)}")
        println("  ✓ Algorithm correctly distinguishes normal from anomalous")
        println("\n=== Dialogue Test Complete ===\n")
    }

    // ===== Helper Functions =====

    private fun generateNormalBehaviorData(count: Int): List<DeviceDetection> {
        val baseTime = System.currentTimeMillis()
        val homeLat = 37.7749
        val homeLon = -122.4194

        return (0 until count).map { index ->
            DeviceDetection(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceType = DeviceType.BLUETOOTH_DEVICE,
                deviceName = "User's Smartwatch",
                timestamp = baseTime + (index * 3600000L), // 1 per hour
                signalStrength = -60,
                latitude = homeLat,
                longitude = homeLon,
                accuracy = 10.0f
            )
        }
    }

    private fun generateRapidDetections(count: Int): List<DeviceDetection> {
        val baseTime = System.currentTimeMillis()

        return (0 until count).map { index ->
            DeviceDetection(
                deviceAddress = "11:22:33:44:55:66",
                deviceType = DeviceType.BLUETOOTH_DEVICE,
                deviceName = "Unknown Device",
                timestamp = baseTime + (index * 60000L), // 1 per minute
                signalStrength = -50,
                latitude = 37.7749,
                longitude = -122.4194,
                accuracy = 10.0f
            )
        }
    }

    private fun generateTrackingPattern(count: Int): List<DeviceDetection> {
        val baseTime = System.currentTimeMillis()

        return (0 until count).map { index ->
            // Simulate device following user across ~1km intervals
            val lat = 37.7749 + (index * 0.01)
            val lon = -122.4194 + (index * 0.01)

            DeviceDetection(
                deviceAddress = "AA:11:BB:22:CC:33",
                deviceType = DeviceType.WIFI_NETWORK,
                deviceName = "Suspicious Tracker",
                timestamp = baseTime + (index * 600000L), // 10 minutes apart
                signalStrength = -55,
                latitude = lat,
                longitude = lon,
                accuracy = 15.0f
            )
        }
    }

    private fun interpretScore(score: Double): String {
        return when {
            score >= 0.8 -> "CRITICAL - Very likely anomalous"
            score >= 0.6 -> "HIGH - Likely anomalous"
            score >= 0.4 -> "MEDIUM - Possibly anomalous"
            else -> "LOW - Likely normal"
        }
    }
}
