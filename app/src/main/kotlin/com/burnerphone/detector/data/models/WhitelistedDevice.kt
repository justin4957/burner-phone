package com.burnerphone.detector.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a device that has been whitelisted by the user
 */
@Entity(tableName = "whitelisted_devices")
data class WhitelistedDevice(
    @PrimaryKey
    val deviceAddress: String,
    val deviceType: DeviceType,
    val deviceName: String? = null,
    val whitelistedAt: Long = System.currentTimeMillis(),
    val reason: String? = null,
    val notes: String? = null
)
