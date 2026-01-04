package com.burnerphone.detector.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore preferences for data retention settings
 */
private val Context.retentionDataStore: DataStore<Preferences> by preferencesDataStore(name = "retention_settings")

class RetentionSettings(private val context: Context) {

    private object PreferencesKeys {
        val DEVICE_DETECTIONS_DAYS = intPreferencesKey("device_detections_retention_days")
        val UNRESOLVED_ANOMALIES_DAYS = intPreferencesKey("unresolved_anomalies_retention_days")
        val RESOLVED_ANOMALIES_DAYS = intPreferencesKey("resolved_anomalies_retention_days")
        val CLEANUP_ENABLED = intPreferencesKey("cleanup_enabled") // 1 = enabled, 0 = disabled
    }

    /**
     * Get device detections retention period in days
     * Default: 30 days
     */
    val deviceDetectionsRetentionDays: Flow<Int> = context.retentionDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEVICE_DETECTIONS_DAYS] ?: DEFAULT_DEVICE_DETECTIONS_DAYS
        }

    /**
     * Get unresolved anomalies retention period in days
     * Default: 90 days
     */
    val unresolvedAnomaliesRetentionDays: Flow<Int> = context.retentionDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.UNRESOLVED_ANOMALIES_DAYS] ?: DEFAULT_UNRESOLVED_ANOMALIES_DAYS
        }

    /**
     * Get resolved anomalies retention period in days
     * Default: 7 days
     */
    val resolvedAnomaliesRetentionDays: Flow<Int> = context.retentionDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.RESOLVED_ANOMALIES_DAYS] ?: DEFAULT_RESOLVED_ANOMALIES_DAYS
        }

    /**
     * Get cleanup enabled status
     * Default: enabled (true)
     */
    val cleanupEnabled: Flow<Boolean> = context.retentionDataStore.data
        .map { preferences ->
            (preferences[PreferencesKeys.CLEANUP_ENABLED] ?: 1) == 1
        }

    /**
     * Update device detections retention period
     */
    suspend fun setDeviceDetectionsRetentionDays(days: Int) {
        context.retentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVICE_DETECTIONS_DAYS] = days.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        }
    }

    /**
     * Update unresolved anomalies retention period
     */
    suspend fun setUnresolvedAnomaliesRetentionDays(days: Int) {
        context.retentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.UNRESOLVED_ANOMALIES_DAYS] = days.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        }
    }

    /**
     * Update resolved anomalies retention period
     */
    suspend fun setResolvedAnomaliesRetentionDays(days: Int) {
        context.retentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.RESOLVED_ANOMALIES_DAYS] = days.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        }
    }

    /**
     * Enable or disable automatic cleanup
     */
    suspend fun setCleanupEnabled(enabled: Boolean) {
        context.retentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.CLEANUP_ENABLED] = if (enabled) 1 else 0
        }
    }

    /**
     * Reset all retention settings to defaults
     */
    suspend fun resetToDefaults() {
        context.retentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVICE_DETECTIONS_DAYS] = DEFAULT_DEVICE_DETECTIONS_DAYS
            preferences[PreferencesKeys.UNRESOLVED_ANOMALIES_DAYS] = DEFAULT_UNRESOLVED_ANOMALIES_DAYS
            preferences[PreferencesKeys.RESOLVED_ANOMALIES_DAYS] = DEFAULT_RESOLVED_ANOMALIES_DAYS
            preferences[PreferencesKeys.CLEANUP_ENABLED] = 1
        }
    }

    companion object {
        // Default retention periods
        const val DEFAULT_DEVICE_DETECTIONS_DAYS = 30
        const val DEFAULT_UNRESOLVED_ANOMALIES_DAYS = 90
        const val DEFAULT_RESOLVED_ANOMALIES_DAYS = 7

        // Constraints
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 365
    }
}
