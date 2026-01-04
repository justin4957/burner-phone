package com.burnerphone.detector.analysis

import com.burnerphone.detector.data.models.DeviceDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import kotlin.random.Random

/**
 * Machine Learning-based anomaly detection using Isolation Forest algorithm.
 * Provides on-device training and inference for privacy-preserving anomaly detection.
 */
class MLAnomalyDetector {

    private var isolationForest: IsolationForest? = null
    private var isModelTrained = false

    /**
     * Extract features from device detection data for ML model input
     */
    fun extractFeatures(detections: List<DeviceDetection>): List<DeviceFeatures> {
        if (detections.isEmpty()) return emptyList()

        // Group by device address
        val deviceGroups = detections.groupBy { it.deviceAddress }

        return deviceGroups.map { (_, deviceDetections) ->
            extractDeviceFeatures(deviceDetections)
        }
    }

    /**
     * Extract statistical features from a device's detection history
     */
    private fun extractDeviceFeatures(detections: List<DeviceDetection>): DeviceFeatures {
        // Temporal features
        val intervals = detections.zipWithNext { a, b ->
            abs(b.timestamp - a.timestamp).toDouble()
        }

        val meanInterval = if (intervals.isNotEmpty()) intervals.average() else 0.0
        val stdDevInterval = if (intervals.isNotEmpty()) {
            sqrt(intervals.map { (it - meanInterval).pow(2) }.average())
        } else 0.0

        // Frequency features
        val timeSpan = if (detections.size > 1) {
            detections.last().timestamp - detections.first().timestamp
        } else 0L
        val frequency = if (timeSpan > 0) {
            (detections.size.toDouble() / timeSpan) * 3600000.0 // per hour
        } else 0.0

        // Geographic features
        val detectionsWithLocation = detections.filter {
            it.latitude != null && it.longitude != null
        }

        val distances = if (detectionsWithLocation.size >= 2) {
            detectionsWithLocation.zipWithNext { a, b ->
                calculateDistance(
                    a.latitude!!, a.longitude!!,
                    b.latitude!!, b.longitude!!
                )
            }
        } else emptyList()

        val meanDistance = if (distances.isNotEmpty()) distances.average() else 0.0
        val maxDistance = if (distances.isNotEmpty()) distances.maxOrNull() ?: 0.0 else 0.0

        // Signal strength features
        val signalStrengths = detections.mapNotNull { it.signalStrength }
        val meanSignalStrength = if (signalStrengths.isNotEmpty()) {
            signalStrengths.average()
        } else 0.0
        val stdDevSignalStrength = if (signalStrengths.isNotEmpty()) {
            sqrt(signalStrengths.map { (it - meanSignalStrength).pow(2) }.average())
        } else 0.0

        // Time of day pattern (entropy-based)
        val hoursOfDay = detections.map { timestamp ->
            ((timestamp / 3600000) % 24).toInt()
        }
        val hourEntropy = calculateEntropy(hoursOfDay)

        return DeviceFeatures(
            detectionCount = detections.size.toDouble(),
            meanInterval = meanInterval,
            stdDevInterval = stdDevInterval,
            frequency = frequency,
            uniqueLocations = detectionsWithLocation.distinctBy {
                Pair(it.latitude, it.longitude)
            }.size.toDouble(),
            meanDistance = meanDistance,
            maxDistance = maxDistance,
            meanSignalStrength = meanSignalStrength,
            stdDevSignalStrength = stdDevSignalStrength,
            hourEntropy = hourEntropy
        )
    }

    /**
     * Train the Isolation Forest model on historical normal behavior
     */
    suspend fun trainModel(
        normalDetections: List<DeviceDetection>,
        numberOfTrees: Int = 100,
        subsampleSize: Int = 256
    ) = withContext(Dispatchers.Default) {
        val features = extractFeatures(normalDetections)
        if (features.isEmpty()) {
            return@withContext
        }

        isolationForest = IsolationForest(
            numberOfTrees = numberOfTrees,
            subsampleSize = min(subsampleSize, features.size)
        )

        isolationForest?.train(features)
        isModelTrained = true
    }

    /**
     * Predict anomaly score for new device detections
     * Returns a score between 0.0 (normal) and 1.0 (anomalous)
     */
    suspend fun predictAnomalyScore(detections: List<DeviceDetection>): Double =
        withContext(Dispatchers.Default) {
            if (!isModelTrained || isolationForest == null) {
                return@withContext 0.0
            }

            val features = extractFeatures(detections)
            if (features.isEmpty()) {
                return@withContext 0.0
            }

            // Average anomaly score across all device features
            val scores = features.map { isolationForest!!.predict(it) }
            scores.average()
        }

    /**
     * Update model with user feedback (incremental learning)
     */
    suspend fun updateWithFeedback(
        detections: List<DeviceDetection>,
        isAnomaly: Boolean
    ) = withContext(Dispatchers.Default) {
        // For false positives (user marks as normal), retrain with this as normal data
        if (!isAnomaly && isModelTrained) {
            val features = extractFeatures(detections)
            // In a production system, this would incrementally update the forest
            // For now, we'll use this as a placeholder for future enhancement
        }
    }

    /**
     * Check if the model is trained and ready for inference
     */
    fun isModelReady(): Boolean = isModelTrained

    /**
     * Calculate Haversine distance between two coordinates
     */
    private fun calculateDistance(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val deltaLatitude = Math.toRadians(latitude2 - latitude1)
        val deltaLongitude = Math.toRadians(longitude2 - longitude1)

        val a = sin(deltaLatitude / 2).pow(2) +
                cos(Math.toRadians(latitude1)) * cos(Math.toRadians(latitude2)) *
                sin(deltaLongitude / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Calculate Shannon entropy for distribution analysis
     */
    private fun calculateEntropy(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0

        val frequencies = values.groupingBy { it }.eachCount()
        val total = values.size.toDouble()

        return frequencies.values.sumOf { count ->
            val probability = count / total
            -probability * ln(probability)
        }
    }
}

/**
 * Feature vector extracted from device detection patterns
 */
data class DeviceFeatures(
    val detectionCount: Double,
    val meanInterval: Double,
    val stdDevInterval: Double,
    val frequency: Double,
    val uniqueLocations: Double,
    val meanDistance: Double,
    val maxDistance: Double,
    val meanSignalStrength: Double,
    val stdDevSignalStrength: Double,
    val hourEntropy: Double
) {
    /**
     * Convert to normalized feature array for ML model
     */
    fun toArray(): DoubleArray {
        return doubleArrayOf(
            normalizeDetectionCount(detectionCount),
            normalizeInterval(meanInterval),
            normalizeInterval(stdDevInterval),
            normalizeFrequency(frequency),
            normalizeLocations(uniqueLocations),
            normalizeDistance(meanDistance),
            normalizeDistance(maxDistance),
            normalizeSignalStrength(meanSignalStrength),
            normalizeSignalStrength(stdDevSignalStrength),
            hourEntropy / 4.0 // normalize entropy (max ~3.2 for 24 hours)
        )
    }

    private fun normalizeDetectionCount(value: Double): Double {
        return min(value / 100.0, 1.0)
    }

    private fun normalizeInterval(value: Double): Double {
        return min(value / 3600000.0, 1.0) // normalize to hours
    }

    private fun normalizeFrequency(value: Double): Double {
        return min(value / 20.0, 1.0) // normalize to 20/hour max
    }

    private fun normalizeLocations(value: Double): Double {
        return min(value / 10.0, 1.0)
    }

    private fun normalizeDistance(value: Double): Double {
        return min(value / 10000.0, 1.0) // normalize to 10km
    }

    private fun normalizeSignalStrength(value: Double): Double {
        return min(abs(value) / 100.0, 1.0)
    }
}

/**
 * Isolation Forest implementation for anomaly detection.
 * Uses ensemble of isolation trees to identify outliers.
 */
class IsolationForest(
    private val numberOfTrees: Int = 100,
    private val subsampleSize: Int = 256
) {
    private val trees = mutableListOf<IsolationTree>()
    private var averagePathLength: Double = 0.0

    /**
     * Train the forest on normal data
     */
    fun train(normalData: List<DeviceFeatures>) {
        trees.clear()

        val featureArrays = normalData.map { it.toArray() }

        repeat(numberOfTrees) {
            val subsample = featureArrays.shuffled().take(subsampleSize)
            val tree = IsolationTree(maxHeight = ceil(log2(subsampleSize.toDouble())).toInt())
            tree.build(subsample)
            trees.add(tree)
        }

        // Calculate average path length for normalization
        averagePathLength = calculateAveragePathLength(subsampleSize)
    }

    /**
     * Predict anomaly score for a feature vector
     * Returns score between 0.0 (normal) and 1.0 (anomalous)
     */
    fun predict(features: DeviceFeatures): Double {
        if (trees.isEmpty()) return 0.0

        val featureArray = features.toArray()

        // Calculate average path length across all trees
        val avgPathLength = trees.map { it.pathLength(featureArray) }.average()

        // Normalize using expected path length
        val anomalyScore = 2.0.pow(-avgPathLength / averagePathLength)

        return min(max(anomalyScore, 0.0), 1.0)
    }

    /**
     * Calculate expected average path length for normalization
     */
    private fun calculateAveragePathLength(sampleSize: Int): Double {
        if (sampleSize <= 1) return 0.0

        // Expected path length formula from Isolation Forest paper
        val harmonicNumber = ln(sampleSize - 1.0) + 0.5772156649 // Euler's constant
        return 2.0 * harmonicNumber - (2.0 * (sampleSize - 1.0) / sampleSize)
    }
}

/**
 * Individual isolation tree for the forest
 */
class IsolationTree(private val maxHeight: Int) {
    private var root: IsolationNode? = null

    /**
     * Build the tree from training data
     */
    fun build(data: List<DoubleArray>) {
        root = buildNode(data, height = 0)
    }

    /**
     * Calculate path length for a feature vector
     */
    fun pathLength(features: DoubleArray, currentNode: IsolationNode? = root, currentHeight: Int = 0): Double {
        val node = currentNode ?: return currentHeight.toDouble()

        // External node (leaf)
        if (node.left == null && node.right == null) {
            return currentHeight + calculateAdjustment(node.size)
        }

        // Internal node - traverse based on split
        val featureValue = features.getOrNull(node.splitFeature) ?: 0.0
        return if (featureValue < node.splitValue) {
            pathLength(features, node.left, currentHeight + 1)
        } else {
            pathLength(features, node.right, currentHeight + 1)
        }
    }

    /**
     * Build a node recursively
     */
    private fun buildNode(data: List<DoubleArray>, height: Int): IsolationNode? {
        if (data.isEmpty()) return null

        // Stop conditions: max height or all samples identical
        if (height >= maxHeight || data.size <= 1) {
            return IsolationNode(size = data.size)
        }

        // Randomly select a feature and split value
        val numFeatures = data.first().size
        val splitFeature = Random.nextInt(numFeatures)

        val featureValues = data.mapNotNull { it.getOrNull(splitFeature) }
        if (featureValues.isEmpty() || featureValues.distinct().size == 1) {
            return IsolationNode(size = data.size)
        }

        val minValue = featureValues.minOrNull() ?: 0.0
        val maxValue = featureValues.maxOrNull() ?: 0.0

        if (minValue >= maxValue) {
            return IsolationNode(size = data.size)
        }

        val splitValue = Random.nextDouble(minValue, maxValue)

        // Split data
        val leftData = data.filter { (it.getOrNull(splitFeature) ?: 0.0) < splitValue }
        val rightData = data.filter { (it.getOrNull(splitFeature) ?: 0.0) >= splitValue }

        return IsolationNode(
            splitFeature = splitFeature,
            splitValue = splitValue,
            left = buildNode(leftData, height + 1),
            right = buildNode(rightData, height + 1),
            size = data.size
        )
    }

    /**
     * Calculate path length adjustment for external nodes
     */
    private fun calculateAdjustment(sampleSize: Int): Double {
        if (sampleSize <= 1) return 0.0

        val harmonicNumber = ln(sampleSize - 1.0) + 0.5772156649
        return 2.0 * harmonicNumber - (2.0 * (sampleSize - 1.0) / sampleSize)
    }
}

/**
 * Node in an isolation tree
 */
data class IsolationNode(
    val splitFeature: Int = -1,
    val splitValue: Double = 0.0,
    val left: IsolationNode? = null,
    val right: IsolationNode? = null,
    val size: Int = 0
)
