package com.burnerphone.detector.data.dao

import androidx.room.*
import com.burnerphone.detector.data.models.WhitelistedDevice
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistedDeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: WhitelistedDevice)

    @Delete
    suspend fun delete(device: WhitelistedDevice)

    @Query("DELETE FROM whitelisted_devices WHERE deviceAddress = :deviceAddress")
    suspend fun deleteByAddress(deviceAddress: String)

    @Query("SELECT * FROM whitelisted_devices ORDER BY whitelistedAt DESC")
    fun getAllWhitelisted(): Flow<List<WhitelistedDevice>>

    @Query("SELECT * FROM whitelisted_devices WHERE deviceAddress = :deviceAddress")
    suspend fun getByAddress(deviceAddress: String): WhitelistedDevice?

    @Query("SELECT EXISTS(SELECT 1 FROM whitelisted_devices WHERE deviceAddress = :deviceAddress)")
    suspend fun isWhitelisted(deviceAddress: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM whitelisted_devices WHERE deviceAddress = :deviceAddress)")
    fun isWhitelistedFlow(deviceAddress: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM whitelisted_devices")
    fun getWhitelistedCount(): Flow<Int>
}
