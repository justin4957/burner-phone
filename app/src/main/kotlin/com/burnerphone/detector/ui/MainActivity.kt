package com.burnerphone.detector.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.data.models.AnomalyDetection
import com.burnerphone.detector.power.BatteryMonitor
import com.burnerphone.detector.service.DeviceMonitoringService
import com.burnerphone.detector.ui.theme.BurnerPhoneTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startMonitoringService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BurnerPhoneTheme {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BurnerPhoneNavHost(
                        navController = navController,
                        onStartMonitoring = { checkPermissionsAndStart() },
                        onStopMonitoring = { stopMonitoringService() }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startMonitoringService()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, DeviceMonitoringService::class.java).apply {
            action = DeviceMonitoringService.ACTION_START_MONITORING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, DeviceMonitoringService::class.java).apply {
            action = DeviceMonitoringService.ACTION_STOP_MONITORING
        }
        startService(intent)
    }
}

@Composable
fun BurnerPhoneNavHost(
    navController: NavHostController,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                onStartMonitoring = onStartMonitoring,
                onStopMonitoring = onStopMonitoring,
                onDeviceClick = { deviceAddress ->
                    navController.navigate("device/$deviceAddress")
                },
                onNavigateToWhitelist = {
                    navController.navigate("whitelist")
                }
            )
        }
        composable("device/{deviceAddress}") { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: return@composable
            DeviceDetailScreen(
                deviceAddress = deviceAddress,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("whitelist") {
            WhitelistScreen(
                onNavigateBack = { navController.popBackStack() },
                onDeviceClick = { deviceAddress ->
                    navController.navigate("device/$deviceAddress")
                }
            )
        }
    }
}

@Composable
fun MainScreen(
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onDeviceClick: (String) -> Unit = {},
    onNavigateToWhitelist: () -> Unit = {}
) {
    var isMonitoring by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as BurnerPhoneApplication
    val anomalies by app.database.anomalyDetectionDao()
        .getUnacknowledgedAnomalies()
        .collectAsState(initial = emptyList())

    val whitelistCount by app.database.whitelistedDeviceDao()
        .getWhitelistedCount()
        .collectAsState(initial = 0)

    // Battery monitor for displaying stats
    val batteryMonitor = remember { BatteryMonitor(context) }
    val batteryLevel by batteryMonitor.batteryLevel.collectAsState()
    val isCharging by batteryMonitor.isCharging.collectAsState()
    val isPowerSaveMode by batteryMonitor.isPowerSaveMode.collectAsState()
    val scanInterval by batteryMonitor.scanInterval.collectAsState()
    val isBluetoothEnabled by batteryMonitor.isBluetoothEnabled.collectAsState()

    DisposableEffect(Unit) {
        batteryMonitor.startMonitoring()
        onDispose {
            batteryMonitor.stopMonitoring()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "BurnerPhone Detector",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Monitoring Status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMonitoring) "Active" else "Inactive",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Button(
                        onClick = {
                            if (isMonitoring) {
                                onStopMonitoring()
                            } else {
                                onStartMonitoring()
                            }
                            isMonitoring = !isMonitoring
                        }
                    ) {
                        Text(if (isMonitoring) "Stop" else "Start")
                    }
                }
            }
        }

        // Battery status card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCharging -> MaterialTheme.colorScheme.primaryContainer
                    isPowerSaveMode -> MaterialTheme.colorScheme.tertiaryContainer
                    batteryLevel < 20 -> MaterialTheme.colorScheme.errorContainer
                    batteryLevel < 50 -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Battery & Power Status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Battery Level:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$batteryLevel%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when {
                            isCharging -> "Charging"
                            isPowerSaveMode -> "Power Save Mode"
                            else -> "Normal"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scan Interval:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatScanInterval(scanInterval),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bluetooth Scanning:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isBluetoothEnabled) "Enabled" else "Disabled (Battery Saver)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isBluetoothEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable(onClick = onNavigateToWhitelist),
            colors = CardDefaults.cardColors(
                containerColor = if (whitelistCount > 0) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (whitelistCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "Whitelisted Devices",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (whitelistCount > 0) {
                            "$whitelistCount device${if (whitelistCount != 1) "s" else ""} excluded from detection"
                        } else {
                            "No devices whitelisted"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "View whitelist"
                )
            }
        }

        Text(
            text = "Active Anomalies (${anomalies.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (anomalies.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No anomalies detected",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(anomalies) { anomaly ->
                    AnomalyCard(
                        anomaly = anomaly,
                        onDeviceClick = onDeviceClick
                    )
                }
            }
        }
    }
}

@Composable
fun AnomalyCard(
    anomaly: AnomalyDetection,
    onDeviceClick: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (anomaly.severity) {
                com.burnerphone.detector.data.models.AnomalySeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                com.burnerphone.detector.data.models.AnomalySeverity.HIGH -> MaterialTheme.colorScheme.tertiaryContainer
                com.burnerphone.detector.data.models.AnomalySeverity.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
                com.burnerphone.detector.data.models.AnomalySeverity.LOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = anomaly.anomalyType.name.replace("_", " "),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = anomaly.severity.name,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = anomaly.description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Clickable device addresses
            Column {
                Text(
                    text = "Devices (tap to view):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                anomaly.deviceAddresses.forEach { deviceAddress ->
                    Text(
                        text = "  • $deviceAddress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onDeviceClick(deviceAddress) }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Detected: ${formatTimestamp(anomaly.detectedAt)}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Score: ${String.format("%.2f", anomaly.anomalyScore)} | Confidence: ${String.format("%.2f", anomaly.confidenceLevel)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatScanInterval(intervalMs: Long): String {
    val seconds = intervalMs / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
}
