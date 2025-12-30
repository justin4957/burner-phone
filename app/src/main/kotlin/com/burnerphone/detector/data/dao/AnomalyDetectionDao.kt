package com.burnerphone.detector.data.dao

import androidx.room.*
import com.burnerphone.detector.data.models.AnomalyDetection
import com.burnerphone.detector.data.models.AnomalyType
import com.burnerphone.detector.data.models.AnomalySeverity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnomalyDetectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anomaly: AnomalyDetection): Long

    @Update
    suspend fun update(anomaly: AnomalyDetection)

    @Delete
    suspend fun delete(anomaly: AnomalyDetection)

    @Query("SELECT * FROM anomaly_detections ORDER BY detectedAt DESC")
    fun getAllAnomalies(): Flow<List<AnomalyDetection>>

    @Query("""
        SELECT * FROM anomaly_detections
        WHERE isAcknowledged = 0
        AND isFalsePositive = 0
        ORDER BY severity DESC, detectedAt DESC
    """)
    fun getUnacknowledgedAnomalies(): Flow<List<AnomalyDetection>>

    @Query("""
        SELECT * FROM anomaly_detections
        WHERE anomalyType = :type
        ORDER BY detectedAt DESC
    """)
    fun getAnomaliesByType(type: AnomalyType): Flow<List<AnomalyDetection>>

    @Query("""
        SELECT * FROM anomaly_detections
        WHERE severity = :severity
        ORDER BY detectedAt DESC
    """)
    fun getAnomaliesBySeverity(severity: AnomalySeverity): Flow<List<AnomalyDetection>>

    @Query("""
        SELECT COUNT(*) FROM anomaly_detections
        WHERE isAcknowledged = 0
        AND isFalsePositive = 0
    """)
    fun getUnacknowledgedCount(): Flow<Int>

    @Query("DELETE FROM anomaly_detections WHERE detectedAt < :cutoffTime")
    suspend fun deleteOldAnomalies(cutoffTime: Long): Int
}
