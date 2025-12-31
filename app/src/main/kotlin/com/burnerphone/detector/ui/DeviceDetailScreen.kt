package com.burnerphone.detector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.data.models.DeviceDetection
import com.burnerphone.detector.data.models.WhitelistedDevice
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceAddress: String,
    onNavigateBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as BurnerPhoneApplication
    val scope = rememberCoroutineScope()

    val detections by app.database.deviceDetectionDao()
        .getDetectionsByAddress(deviceAddress)
        .collectAsState(initial = emptyList())

    val isWhitelisted by app.database.whitelistedDeviceDao()
        .isWhitelistedFlow(deviceAddress)
        .collectAsState(initial = false)

    val deviceName = detections.firstOrNull()?.deviceName ?: "Unknown Device"
    val deviceType = detections.firstOrNull()?.deviceType?.name?.replace("_", " ") ?: "Unknown"

    // Calculate statistics
    val firstSeen = detections.minByOrNull { it.timestamp }?.timestamp
    val lastSeen = detections.maxByOrNull { it.timestamp }?.timestamp
    val totalDetections = detections.size
    val avgSignalStrength = detections.mapNotNull { it.signalStrength }.average().takeIf { !it.isNaN() }
    val uniqueLocations = detections.mapNotNull {
        if (it.latitude != null && it.longitude != null) Pair(it.latitude, it.longitude) else null
    }.distinct().size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isWhitelisted) {
                                    app.database.whitelistedDeviceDao().deleteByAddress(deviceAddress)
                                } else {
                                    val whitelistedDevice = WhitelistedDevice(
                                        deviceAddress = deviceAddress,
                                        deviceType = detections.firstOrNull()?.deviceType
                                            ?: com.burnerphone.detector.data.models.DeviceType.WIFI_NETWORK,
                                        deviceName = deviceName,
                                        reason = "Manually whitelisted from device detail"
                                    )
                                    app.database.whitelistedDeviceDao().insert(whitelistedDevice)
                                }
                            }
                        }
                    ) {
                        if (isWhitelisted) {
                            Icon(Icons.Default.Check, "Whitelisted", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Check, "Not Whitelisted")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isWhitelisted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = deviceAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = deviceType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isWhitelisted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "✓ Whitelisted - Excluded from anomaly detection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Statistics Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        StatisticRow("Total Detections", totalDetections.toString())
                        StatisticRow("First Seen", firstSeen?.let { formatTimestamp(it) } ?: "Never")
                        StatisticRow("Last Seen", lastSeen?.let { formatTimestamp(it) } ?: "Never")
                        StatisticRow("Unique Locations", uniqueLocations.toString())
                        avgSignalStrength?.let {
                            StatisticRow("Avg Signal Strength", "${it.toInt()} dBm")
                        }
                    }
                }
            }

            // Signal Strength Pattern Card
            if (detections.isNotEmpty() && detections.any { it.signalStrength != null }) {
                item {
                    SignalStrengthCard(detections)
                }
            }

            // Detection History Header
            item {
                Text(
                    text = "Detection History (${detections.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Detection Timeline
            items(detections.sortedByDescending { it.timestamp }) { detection ->
                DetectionCard(detection)
            }
        }
    }
}

@Composable
fun StatisticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SignalStrengthCard(detections: List<DeviceDetection>) {
    val signalData = detections
        .filter { it.signalStrength != null }
        .sortedBy { it.timestamp }

    val minSignal = signalData.minOfOrNull { it.signalStrength!! } ?: 0
    val maxSignal = signalData.maxOfOrNull { it.signalStrength!! } ?: 0
    val avgSignal = signalData.mapNotNull { it.signalStrength }.average()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Signal Strength Pattern",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            StatisticRow("Minimum", "$minSignal dBm")
            StatisticRow("Maximum", "$maxSignal dBm")
            StatisticRow("Average", "${avgSignal.toInt()} dBm")

            Spacer(modifier = Modifier.height(8.dp))

            // Simple visualization
            Text(
                text = "Recent Trend:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val recentSignals = signalData.takeLast(10)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                recentSignals.forEach { detection ->
                    val strength = detection.signalStrength ?: 0
                    val normalizedHeight = ((strength - minSignal).toFloat() / (maxSignal - minSignal).coerceAtLeast(1))
                        .coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height((50 * normalizedHeight).dp.coerceAtLeast(4.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionCard(detection: DeviceDetection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = formatTimestamp(detection.timestamp),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                detection.signalStrength?.let {
                    Text(
                        text = "Signal: $it dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                detection.frequency?.let {
                    Text(
                        text = "${it / 1000.0} GHz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (detection.latitude != null && detection.longitude != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Location: ${String.format("%.4f", detection.latitude)}, ${String.format("%.4f", detection.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                detection.accuracy?.let {
                    Text(
                        text = "Accuracy: ±${it.toInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
