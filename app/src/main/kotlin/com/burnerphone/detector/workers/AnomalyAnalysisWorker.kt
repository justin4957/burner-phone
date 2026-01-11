package com.burnerphone.detector.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.analysis.AnomalyAnalyzer

/**
 * WorkManager worker for periodic anomaly analysis
 * Analyzes device detection patterns and creates anomaly records
 *
 * Runs every 6 hours to detect surveillance patterns:
 * - Statistical anomalies in device behavior
 * - ML-based anomaly detection
 * - Temporal clustering of devices
 * - Geographic tracking patterns
 */
class AnomalyAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting periodic anomaly analysis...")

            // Get database from application
            val app = applicationContext as BurnerPhoneApplication
            val database = app.database

            // Create anomaly analyzer with DAOs
            val analyzer = AnomalyAnalyzer(
                deviceDetectionDao = database.deviceDetectionDao(),
                anomalyDetectionDao = database.anomalyDetectionDao(),
                whitelistedDeviceDao = database.whitelistedDeviceDao()
            )

            // Get statistics before analysis
            val deviceCount = database.deviceDetectionDao().getUniqueDeviceCount(
                com.burnerphone.detector.data.models.DeviceType.WIFI_NETWORK
            ) + database.deviceDetectionDao().getUniqueDeviceCount(
                com.burnerphone.detector.data.models.DeviceType.BLUETOOTH_DEVICE
            )
            val anomalyCountBefore = database.anomalyDetectionDao().getTotalCount()

            Log.d(TAG, "Analysis starting: $deviceCount unique devices, $anomalyCountBefore existing anomalies")

            // Train ML model if not already trained
            if (!analyzer.isMLModelReady()) {
                Log.d(TAG, "ML model not ready, attempting to train...")
                analyzer.trainMLModel()
                if (analyzer.isMLModelReady()) {
                    Log.d(TAG, "ML model trained successfully")
                } else {
                    Log.d(TAG, "ML model training skipped (insufficient data)")
                }
            } else {
                Log.d(TAG, "ML model already trained and ready")
            }

            // Run full anomaly analysis
            analyzer.analyzeAll()

            // Get statistics after analysis
            val anomalyCountAfter = database.anomalyDetectionDao().getTotalCount()
            val newAnomalies = anomalyCountAfter - anomalyCountBefore
            val activeAnomalies = database.anomalyDetectionDao().getActiveCount()

            Log.d(TAG, "Analysis completed successfully")
            Log.d(TAG, "  - New anomalies detected: $newAnomalies")
            Log.d(TAG, "  - Total anomalies: $anomalyCountAfter")
            Log.d(TAG, "  - Active (unacknowledged): $activeAnomalies")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Anomaly analysis failed", e)
            // Retry on failure, but allow up to 3 retries
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Retrying anomaly analysis (attempt ${runAttemptCount + 1})")
                Result.retry()
            } else {
                Log.e(TAG, "Max retry attempts reached, marking as failed")
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "AnomalyAnalysisWorker"
        const val WORK_NAME = "anomaly_analysis_work"
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
