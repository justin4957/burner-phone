package com.burnerphone.detector.data.repository

import com.burnerphone.detector.data.dao.AnomalyDetectionDao
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.models.*
import com.burnerphone.detector.data.preferences.RetentionSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.TimeUnit

/**
 * Unit tests for DataCleanupRepository
 */
class DataCleanupRepositoryTest {

    private lateinit var deviceDetectionDao: DeviceDetectionDao
    private lateinit var anomalyDetectionDao: AnomalyDetectionDao
    private lateinit var retentionSettings: RetentionSettings
    private lateinit var repository: DataCleanupRepository

    @Before
    fun setup() {
        deviceDetectionDao = mock(DeviceDetectionDao::class.java)
        anomalyDetectionDao = mock(AnomalyDetectionDao::class.java)
        retentionSettings = mock(RetentionSettings::class.java)

        repository = DataCleanupRepository(
            deviceDetectionDao = deviceDetectionDao,
            anomalyDetectionDao = anomalyDetectionDao,
            retentionSettings = retentionSettings
        )
    }

    // ===== Cleanup Tests =====

    @Test
    fun `performCleanup uses correct retention periods`() = runTest {
        // Mock retention settings
        `when`(retentionSettings.deviceDetectionsRetentionDays).thenReturn(flowOf(30))
        `when`(retentionSettings.unresolvedAnomaliesRetentionDays).thenReturn(flowOf(90))
        `when`(retentionSettings.resolvedAnomaliesRetentionDays).thenReturn(flowOf(7))

        // Mock DAO responses
        `when`(deviceDetectionDao.deleteOldDetections(anyLong())).thenReturn(10)
        `when`(anomalyDetectionDao.deleteOldUnresolvedAnomalies(anyLong())).thenReturn(5)
        `when`(anomalyDetectionDao.deleteOldResolvedAnomalies(anyLong())).thenReturn(15)

        // Perform cleanup
        val stats = repository.performCleanup()

        // Verify correct methods were called
        verify(deviceDetectionDao).deleteOldDetections(anyLong())
        verify(anomalyDetectionDao).deleteOldUnresolvedAnomalies(anyLong())
        verify(anomalyDetectionDao).deleteOldResolvedAnomalies(anyLong())

        // Verify statistics
        assertEquals(10, stats.deviceDetectionsDeleted)
        assertEquals(5, stats.unresolvedAnomaliesDeleted)
        assertEquals(15, stats.resolvedAnomaliesDeleted)
        assertEquals(30, stats.totalDeleted)
    }

    @Test
    fun `performCleanup calculates correct cutoff times`() = runTest {
        val now = System.currentTimeMillis()
        val deviceRetentionDays = 30
        val unresolvedRetentionDays = 90
        val resolvedRetentionDays = 7

        // Mock retention settings
        `when`(retentionSettings.deviceDetectionsRetentionDays).thenReturn(flowOf(deviceRetentionDays))
        `when`(retentionSettings.unresolvedAnomaliesRetentionDays).thenReturn(flowOf(unresolvedRetentionDays))
        `when`(retentionSettings.resolvedAnomaliesRetentionDays).thenReturn(flowOf(resolvedRetentionDays))

        // Mock DAO responses
        `when`(deviceDetectionDao.deleteOldDetections(anyLong())).thenReturn(0)
        `when`(anomalyDetectionDao.deleteOldUnresolvedAnomalies(anyLong())).thenReturn(0)
        `when`(anomalyDetectionDao.deleteOldResolvedAnomalies(anyLong())).thenReturn(0)

        // Perform cleanup
        repository.performCleanup()

        // Calculate expected cutoff times
        val expectedDeviceCutoff = now - TimeUnit.DAYS.toMillis(deviceRetentionDays.toLong())
        val expectedUnresolvedCutoff = now - TimeUnit.DAYS.toMillis(unresolvedRetentionDays.toLong())
        val expectedResolvedCutoff = now - TimeUnit.DAYS.toMillis(resolvedRetentionDays.toLong())

        // Verify cutoff times are within reasonable range (within 1 second tolerance)
        verify(deviceDetectionDao).deleteOldDetections(
            longThat { cutoff -> Math.abs(cutoff - expectedDeviceCutoff) < 1000 }
        )
        verify(anomalyDetectionDao).deleteOldUnresolvedAnomalies(
            longThat { cutoff -> Math.abs(cutoff - expectedUnresolvedCutoff) < 1000 }
        )
        verify(anomalyDetectionDao).deleteOldResolvedAnomalies(
            longThat { cutoff -> Math.abs(cutoff - expectedResolvedCutoff) < 1000 }
        )
    }

    @Test
    fun `performCleanup returns zero when no data to delete`() = runTest {
        // Mock retention settings
        `when`(retentionSettings.deviceDetectionsRetentionDays).thenReturn(flowOf(30))
        `when`(retentionSettings.unresolvedAnomaliesRetentionDays).thenReturn(flowOf(90))
        `when`(retentionSettings.resolvedAnomaliesRetentionDays).thenReturn(flowOf(7))

        // Mock DAO responses - no data deleted
        `when`(deviceDetectionDao.deleteOldDetections(anyLong())).thenReturn(0)
        `when`(anomalyDetectionDao.deleteOldUnresolvedAnomalies(anyLong())).thenReturn(0)
        `when`(anomalyDetectionDao.deleteOldResolvedAnomalies(anyLong())).thenReturn(0)

        // Perform cleanup
        val stats = repository.performCleanup()

        // Verify all counts are zero
        assertEquals(0, stats.deviceDetectionsDeleted)
        assertEquals(0, stats.unresolvedAnomaliesDeleted)
        assertEquals(0, stats.resolvedAnomaliesDeleted)
        assertEquals(0, stats.totalDeleted)
    }

    @Test
    fun `performCleanup handles large deletion counts`() = runTest {
        // Mock retention settings
        `when`(retentionSettings.deviceDetectionsRetentionDays).thenReturn(flowOf(30))
        `when`(retentionSettings.unresolvedAnomaliesRetentionDays).thenReturn(flowOf(90))
        `when`(retentionSettings.resolvedAnomaliesRetentionDays).thenReturn(flowOf(7))

        // Mock DAO responses - large deletion counts
        `when`(deviceDetectionDao.deleteOldDetections(anyLong())).thenReturn(10000)
        `when`(anomalyDetectionDao.deleteOldUnresolvedAnomalies(anyLong())).thenReturn(500)
        `when`(anomalyDetectionDao.deleteOldResolvedAnomalies(anyLong())).thenReturn(2500)

        // Perform cleanup
        val stats = repository.performCleanup()

        // Verify large counts
        assertEquals(10000, stats.deviceDetectionsDeleted)
        assertEquals(500, stats.unresolvedAnomaliesDeleted)
        assertEquals(2500, stats.resolvedAnomaliesDeleted)
        assertEquals(13000, stats.totalDeleted)
    }

    // ===== Storage Statistics Tests =====

    @Test
    fun `getStorageStatistics returns correct counts`() = runTest {
        // Mock DAO responses
        `when`(deviceDetectionDao.getTotalCount()).thenReturn(1000)
        `when`(deviceDetectionDao.getCountByType(DeviceType.WIFI_NETWORK)).thenReturn(600)
        `when`(deviceDetectionDao.getCountByType(DeviceType.BLUETOOTH_DEVICE)).thenReturn(400)
        `when`(anomalyDetectionDao.getTotalCount()).thenReturn(50)
        `when`(anomalyDetectionDao.getActiveCount()).thenReturn(30)
        `when`(anomalyDetectionDao.getResolvedCount()).thenReturn(20)

        // Get statistics
        val stats = repository.getStorageStatistics()

        // Verify statistics
        assertEquals(1000, stats.totalDeviceDetections)
        assertEquals(600, stats.wifiDetections)
        assertEquals(400, stats.bluetoothDetections)
        assertEquals(50, stats.totalAnomalies)
        assertEquals(30, stats.activeAnomalies)
        assertEquals(20, stats.resolvedAnomalies)
    }

    @Test
    fun `getStorageStatistics calculates estimated size correctly`() = runTest {
        // Mock DAO responses
        `when`(deviceDetectionDao.getTotalCount()).thenReturn(1000)
        `when`(deviceDetectionDao.getCountByType(DeviceType.WIFI_NETWORK)).thenReturn(600)
        `when`(deviceDetectionDao.getCountByType(DeviceType.BLUETOOTH_DEVICE)).thenReturn(400)
        `when`(anomalyDetectionDao.getTotalCount()).thenReturn(100)
        `when`(anomalyDetectionDao.getActiveCount()).thenReturn(60)
        `when`(anomalyDetectionDao.getResolvedCount()).thenReturn(40)

        // Get statistics
        val stats = repository.getStorageStatistics()

        // Calculate expected size
        // (1000 detections * 500 bytes + 100 anomalies * 1024 bytes) / 1024 = ~586 KB
        val expectedSize = ((1000 * 500) + (100 * 1024)) / 1024
        assertEquals(expectedSize, stats.estimatedSizeKB)
    }

    @Test
    fun `getStorageStatistics handles zero counts`() = runTest {
        // Mock DAO responses - all zeros
        `when`(deviceDetectionDao.getTotalCount()).thenReturn(0)
        `when`(deviceDetectionDao.getCountByType(DeviceType.WIFI_NETWORK)).thenReturn(0)
        `when`(deviceDetectionDao.getCountByType(DeviceType.BLUETOOTH_DEVICE)).thenReturn(0)
        `when`(anomalyDetectionDao.getTotalCount()).thenReturn(0)
        `when`(anomalyDetectionDao.getActiveCount()).thenReturn(0)
        `when`(anomalyDetectionDao.getResolvedCount()).thenReturn(0)

        // Get statistics
        val stats = repository.getStorageStatistics()

        // Verify all counts are zero
        assertEquals(0, stats.totalDeviceDetections)
        assertEquals(0, stats.wifiDetections)
        assertEquals(0, stats.bluetoothDetections)
        assertEquals(0, stats.totalAnomalies)
        assertEquals(0, stats.activeAnomalies)
        assertEquals(0, stats.resolvedAnomalies)
        assertEquals(0, stats.estimatedSizeKB)
    }

    // ===== Cleanup Enabled Tests =====

    @Test
    fun `isCleanupEnabled returns true when enabled`() = runTest {
        `when`(retentionSettings.cleanupEnabled).thenReturn(flowOf(true))

        val enabled = repository.isCleanupEnabled()

        assertTrue(enabled)
    }

    @Test
    fun `isCleanupEnabled returns false when disabled`() = runTest {
        `when`(retentionSettings.cleanupEnabled).thenReturn(flowOf(false))

        val enabled = repository.isCleanupEnabled()

        assertFalse(enabled)
    }

    // ===== Cleanup Statistics Tests =====

    @Test
    fun `CleanupStatistics totalDeleted calculates correctly`() {
        val stats = CleanupStatistics(
            deviceDetectionsDeleted = 100,
            unresolvedAnomaliesDeleted = 20,
            resolvedAnomaliesDeleted = 30,
            cleanupTimestamp = System.currentTimeMillis()
        )

        assertEquals(150, stats.totalDeleted)
    }

    @Test
    fun `CleanupStatistics handles zero deletions`() {
        val stats = CleanupStatistics(
            deviceDetectionsDeleted = 0,
            unresolvedAnomaliesDeleted = 0,
            resolvedAnomaliesDeleted = 0,
            cleanupTimestamp = System.currentTimeMillis()
        )

        assertEquals(0, stats.totalDeleted)
    }

    // Helper method for mockito longThat matcher
    private fun longThat(predicate: (Long) -> Boolean): Long {
        org.mockito.ArgumentMatchers.longThat(predicate)
        return 0L
    }
}
