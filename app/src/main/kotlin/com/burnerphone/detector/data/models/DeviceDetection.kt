package com.burnerphone.detector.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.burnerphone.detector.data.converters.Converters

/**
 * Represents a detected device (WiFi, Bluetooth, or network MAC address)
 */
@Entity(tableName = "device_detections")
@TypeConverters(Converters::class)
data class DeviceDetection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Device identifiers
    val deviceAddress: String,  // MAC address or unique identifier
    val deviceType: DeviceType,
    val deviceName: String? = null,  // SSID for WiFi, device name for Bluetooth

    // Detection metadata
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,  // Location accuracy in meters

    // Signal information
    val signalStrength: Int? = null,  // RSSI value
    val frequency: Int? = null,  // For WiFi (2.4GHz or 5GHz)

    // Additional context
    val capabilities: String? = null,  // For WiFi capabilities
    val isConnected: Boolean = false  // Whether device is currently connected
)

enum class DeviceType {
    WIFI_NETWORK,
    BLUETOOTH_DEVICE,
    NETWORK_MAC_ADDRESS
}
