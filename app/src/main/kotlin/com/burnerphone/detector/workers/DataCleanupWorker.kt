package com.burnerphone.detector.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.data.preferences.RetentionSettings
import com.burnerphone.detector.data.repository.DataCleanupRepository

/**
 * WorkManager worker for automated data cleanup
 * Runs daily during low-usage periods (typically at night)
 */
class DataCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting automated data cleanup...")

            // Get database and DAOs from application
            val app = applicationContext as BurnerPhoneApplication
            val database = app.database
            val retentionSettings = RetentionSettings(applicationContext)

            // Create cleanup repository
            val cleanupRepository = DataCleanupRepository(
                deviceDetectionDao = database.deviceDetectionDao(),
                anomalyDetectionDao = database.anomalyDetectionDao(),
                retentionSettings = retentionSettings
            )

            // Check if cleanup is enabled
            if (!cleanupRepository.isCleanupEnabled()) {
                Log.d(TAG, "Data cleanup is disabled in settings. Skipping.")
                return Result.success()
            }

            // Get storage statistics before cleanup
            val statsBefore = cleanupRepository.getStorageStatistics()
            Log.d(TAG, "Storage before cleanup: ${statsBefore.totalDeviceDetections} detections, ${statsBefore.totalAnomalies} anomalies")

            // Perform cleanup
            val cleanupStats = cleanupRepository.performCleanup()
            Log.d(TAG, "Cleanup completed: ${cleanupStats.totalDeleted} records deleted")
            Log.d(TAG, "  - Device detections: ${cleanupStats.deviceDetectionsDeleted}")
            Log.d(TAG, "  - Unresolved anomalies: ${cleanupStats.unresolvedAnomaliesDeleted}")
            Log.d(TAG, "  - Resolved anomalies: ${cleanupStats.resolvedAnomaliesDeleted}")

            // Get storage statistics after cleanup
            val statsAfter = cleanupRepository.getStorageStatistics()
            Log.d(TAG, "Storage after cleanup: ${statsAfter.totalDeviceDetections} detections, ${statsAfter.totalAnomalies} anomalies")
            Log.d(TAG, "Estimated storage saved: ${statsBefore.estimatedSizeKB - statsAfter.estimatedSizeKB} KB")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Data cleanup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DataCleanupWorker"
        const val WORK_NAME = "data_cleanup_work"
    }
}
