package com.burnerphone.detector.scanning

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.models.DeviceDetection
import com.burnerphone.detector.data.models.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Scans for Bluetooth devices and records their MAC addresses and metadata
 */
class BluetoothScanner(
    private val context: Context,
    private val deviceDetectionDao: DeviceDetectionDao
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val discoveredDevices = mutableListOf<DeviceDetection>()

    @SuppressLint("MissingPermission")
    suspend fun scan(location: LocationData?) {
        withContext(Dispatchers.IO) {
            try {
                val adapter = bluetoothAdapter ?: return@withContext

                if (!adapter.isEnabled) {
                    return@withContext
                }

                discoveredDevices.clear()

                // Register broadcast receiver for discovered devices
                val receiver = object : BroadcastReceiver() {
                    @SuppressLint("MissingPermission")
                    override fun onReceive(context: Context, intent: Intent) {
                        when (intent.action) {
                            BluetoothDevice.ACTION_FOUND -> {
                                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }

                                device?.let {
                                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                                    val detection = DeviceDetection(
                                        deviceAddress = it.address,
                                        deviceType = DeviceType.BLUETOOTH_DEVICE,
                                        deviceName = try { it.name } catch (e: SecurityException) { null },
                                        timestamp = System.currentTimeMillis(),
                                        latitude = location?.latitude,
                                        longitude = location?.longitude,
                                        accuracy = location?.accuracy,
                                        signalStrength = rssi,
                                        isConnected = false
                                    )

                                    discoveredDevices.add(detection)
                                }
                            }
                        }
                    }
                }

                // Register receiver
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(receiver, filter)

                // Start discovery and wait for completion
                suspendCancellableCoroutine { continuation ->
                    val finishReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            context.unregisterReceiver(this)
                            context.unregisterReceiver(receiver)
                            continuation.resume(Unit)
                        }
                    }

                    context.registerReceiver(
                        finishReceiver,
                        IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    )

                    adapter.startDiscovery()

                    continuation.invokeOnCancellation {
                        try {
                            context.unregisterReceiver(finishReceiver)
                            context.unregisterReceiver(receiver)
                            adapter.cancelDiscovery()
                        } catch (e: Exception) {
                            // Receiver already unregistered
                        }
                    }
                }

                // Save discovered devices
                if (discoveredDevices.isNotEmpty()) {
                    deviceDetectionDao.insertAll(discoveredDevices)
                }

            } catch (e: SecurityException) {
                // Missing permissions - should be handled at UI level
            } catch (e: Exception) {
                // Log error
            }
        }
    }
}
