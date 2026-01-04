package com.burnerphone.detector.data.repository

import com.burnerphone.detector.data.dao.AnomalyDetectionDao
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.models.CleanupStatistics
import com.burnerphone.detector.data.models.DeviceType
import com.burnerphone.detector.data.models.StorageStatistics
import com.burnerphone.detector.data.preferences.RetentionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Repository for managing data cleanup and retention
 */
class DataCleanupRepository(
    private val deviceDetectionDao: DeviceDetectionDao,
    private val anomalyDetectionDao: AnomalyDetectionDao,
    private val retentionSettings: RetentionSettings
) {

    /**
     * Perform automated cleanup based on retention settings
     */
    suspend fun performCleanup(): CleanupStatistics = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Get retention settings
        val deviceRetentionDays = retentionSettings.deviceDetectionsRetentionDays.first()
        val unresolvedRetentionDays = retentionSettings.unresolvedAnomaliesRetentionDays.first()
        val resolvedRetentionDays = retentionSettings.resolvedAnomaliesRetentionDays.first()

        // Calculate cutoff times
        val deviceCutoff = now - TimeUnit.DAYS.toMillis(deviceRetentionDays.toLong())
        val unresolvedCutoff = now - TimeUnit.DAYS.toMillis(unresolvedRetentionDays.toLong())
        val resolvedCutoff = now - TimeUnit.DAYS.toMillis(resolvedRetentionDays.toLong())

        // Perform cleanup
        val deviceDetectionsDeleted = deviceDetectionDao.deleteOldDetections(deviceCutoff)
        val unresolvedAnomaliesDeleted = anomalyDetectionDao.deleteOldUnresolvedAnomalies(unresolvedCutoff)
        val resolvedAnomaliesDeleted = anomalyDetectionDao.deleteOldResolvedAnomalies(resolvedCutoff)

        CleanupStatistics(
            deviceDetectionsDeleted = deviceDetectionsDeleted,
            unresolvedAnomaliesDeleted = unresolvedAnomaliesDeleted,
            resolvedAnomaliesDeleted = resolvedAnomaliesDeleted,
            cleanupTimestamp = now
        )
    }

    /**
     * Get current storage statistics
     */
    suspend fun getStorageStatistics(): StorageStatistics = withContext(Dispatchers.IO) {
        val totalDeviceDetections = deviceDetectionDao.getTotalCount()
        val wifiDetections = deviceDetectionDao.getCountByType(DeviceType.WIFI_NETWORK)
        val bluetoothDetections = deviceDetectionDao.getCountByType(DeviceType.BLUETOOTH_DEVICE)
        val totalAnomalies = anomalyDetectionDao.getTotalCount()
        val activeAnomalies = anomalyDetectionDao.getActiveCount()
        val resolvedAnomalies = anomalyDetectionDao.getResolvedCount()

        StorageStatistics(
            totalDeviceDetections = totalDeviceDetections,
            wifiDetections = wifiDetections,
            bluetoothDetections = bluetoothDetections,
            totalAnomalies = totalAnomalies,
            activeAnomalies = activeAnomalies,
            resolvedAnomalies = resolvedAnomalies
        )
    }

    /**
     * Check if cleanup is enabled
     */
    suspend fun isCleanupEnabled(): Boolean {
        return retentionSettings.cleanupEnabled.first()
    }
}
