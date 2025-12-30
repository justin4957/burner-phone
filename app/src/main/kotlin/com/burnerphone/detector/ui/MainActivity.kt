package com.burnerphone.detector.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.data.models.AnomalyDetection
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
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
fun MainScreen(
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    var isMonitoring by remember { mutableStateOf(false) }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as BurnerPhoneApplication
    val anomalies by app.database.anomalyDetectionDao()
        .getUnacknowledgedAnomalies()
        .collectAsState(initial = emptyList())

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
                    AnomalyCard(anomaly)
                }
            }
        }
    }
}

@Composable
fun AnomalyCard(anomaly: AnomalyDetection) {
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

            Text(
                text = "Devices: ${anomaly.deviceAddresses.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )

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
