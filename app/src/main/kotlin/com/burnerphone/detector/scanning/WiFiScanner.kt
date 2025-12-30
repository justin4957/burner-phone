package com.burnerphone.detector.scanning

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.models.DeviceDetection
import com.burnerphone.detector.data.models.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans for WiFi networks and records their MAC addresses and metadata
 */
class WiFiScanner(
    private val context: Context,
    private val deviceDetectionDao: DeviceDetectionDao
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun scan(location: LocationData?) {
        withContext(Dispatchers.IO) {
            try {
                // Trigger WiFi scan
                val scanStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // On Android 9+, apps can only scan 4 times per 2 minutes
                    wifiManager.startScan()
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                }

                if (scanStarted) {
                    // Get scan results
                    @Suppress("DEPRECATION")
                    val scanResults = wifiManager.scanResults

                    val detections = scanResults.map { result ->
                        DeviceDetection(
                            deviceAddress = result.BSSID,
                            deviceType = DeviceType.WIFI_NETWORK,
                            deviceName = result.SSID,
                            timestamp = System.currentTimeMillis(),
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            accuracy = location?.accuracy,
                            signalStrength = result.level,
                            frequency = result.frequency,
                            capabilities = result.capabilities,
                            isConnected = false
                        )
                    }

                    if (detections.isNotEmpty()) {
                        deviceDetectionDao.insertAll(detections)
                    }
                }
            } catch (e: SecurityException) {
                // Missing permissions - should be handled at UI level
            } catch (e: Exception) {
                // Log error
            }
        }
    }
}
