package com.burnerphone.detector.data.dao

import androidx.room.*
import com.burnerphone.detector.data.models.DeviceDetection
import com.burnerphone.detector.data.models.DeviceType
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDetectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detection: DeviceDetection): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(detections: List<DeviceDetection>)

    @Update
    suspend fun update(detection: DeviceDetection)

    @Delete
    suspend fun delete(detection: DeviceDetection)

    @Query("SELECT * FROM device_detections ORDER BY timestamp DESC")
    fun getAllDetections(): Flow<List<DeviceDetection>>

    @Query("SELECT * FROM device_detections WHERE deviceType = :type ORDER BY timestamp DESC")
    fun getDetectionsByType(type: DeviceType): Flow<List<DeviceDetection>>

    @Query("SELECT * FROM device_detections WHERE deviceAddress = :address ORDER BY timestamp DESC")
    fun getDetectionsByAddress(address: String): Flow<List<DeviceDetection>>

    @Query("""
        SELECT * FROM device_detections
        WHERE deviceAddress = :address
        AND timestamp >= :startTime
        AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getDetectionsInTimeRange(
        address: String,
        startTime: Long,
        endTime: Long
    ): List<DeviceDetection>

    @Query("""
        SELECT * FROM device_detections
        WHERE deviceType = :type
        AND timestamp >= :startTime
        AND timestamp <= :endTime
        ORDER BY timestamp ASC
    """)
    suspend fun getAllDetectionsInTimeRange(
        type: DeviceType,
        startTime: Long,
        endTime: Long
    ): List<DeviceDetection>

    @Query("""
        SELECT DISTINCT deviceAddress FROM device_detections
        WHERE deviceType = :type
    """)
    suspend fun getUniqueDeviceAddresses(type: DeviceType): List<String>

    @Query("""
        SELECT * FROM device_detections
        WHERE timestamp >= :startTime
        ORDER BY timestamp DESC
    """)
    fun getRecentDetections(startTime: Long): Flow<List<DeviceDetection>>

    @Query("SELECT COUNT(DISTINCT deviceAddress) FROM device_detections WHERE deviceType = :type")
    suspend fun getUniqueDeviceCount(type: DeviceType): Int

    @Query("DELETE FROM device_detections WHERE timestamp < :cutoffTime")
    suspend fun deleteOldDetections(cutoffTime: Long): Int

    @Query("""
        SELECT * FROM device_detections
        WHERE latitude IS NOT NULL
        AND longitude IS NOT NULL
        AND timestamp >= :startTime
        ORDER BY timestamp DESC
    """)
    suspend fun getDetectionsWithLocation(startTime: Long): List<DeviceDetection>

    @Query("SELECT COUNT(*) FROM device_detections")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM device_detections WHERE deviceType = :type")
    suspend fun getCountByType(type: DeviceType): Int
}
