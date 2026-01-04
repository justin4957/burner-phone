package com.burnerphone.detector.data.models

/**
 * Storage statistics for database monitoring
 */
data class StorageStatistics(
    val totalDeviceDetections: Int,
    val wifiDetections: Int,
    val bluetoothDetections: Int,
    val totalAnomalies: Int,
    val activeAnomalies: Int,
    val resolvedAnomalies: Int
) {
    /**
     * Estimated storage size in KB (rough estimate)
     * Assumes ~500 bytes per detection and ~1KB per anomaly
     */
    val estimatedSizeKB: Int
        get() = ((totalDeviceDetections * 500) + (totalAnomalies * 1024)) / 1024
}

/**
 * Cleanup statistics showing what was deleted
 */
data class CleanupStatistics(
    val deviceDetectionsDeleted: Int,
    val unresolvedAnomaliesDeleted: Int,
    val resolvedAnomaliesDeleted: Int,
    val cleanupTimestamp: Long
) {
    val totalDeleted: Int
        get() = deviceDetectionsDeleted + unresolvedAnomaliesDeleted + resolvedAnomaliesDeleted
}
