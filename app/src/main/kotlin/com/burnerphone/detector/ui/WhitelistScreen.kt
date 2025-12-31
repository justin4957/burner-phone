package com.burnerphone.detector.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.data.models.WhitelistedDevice
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    onNavigateBack: () -> Unit,
    onDeviceClick: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as BurnerPhoneApplication
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val whitelistedDevices by app.database.whitelistedDeviceDao()
        .getAllWhitelisted()
        .collectAsState(initial = emptyList())

    var showDeleteDialog by remember { mutableStateOf<WhitelistedDevice?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportExportMenu by remember { mutableStateOf(false) }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = Json { prettyPrint = true }
                    val whitelistData = whitelistedDevices.map { device ->
                        mapOf(
                            "deviceAddress" to device.deviceAddress,
                            "deviceType" to device.deviceType.name,
                            "deviceName" to (device.deviceName ?: ""),
                            "reason" to (device.reason ?: ""),
                            "notes" to (device.notes ?: ""),
                            "whitelistedAt" to device.whitelistedAt
                        )
                    }
                    val jsonString = json.encodeToString(whitelistData)

                    context.contentResolver.openFileDescriptor(it, "w")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { fos ->
                            fos.write(jsonString.toByteArray())
                        }
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val jsonString = context.contentResolver.openFileDescriptor(it, "r")?.use { pfd ->
                        FileInputStream(pfd.fileDescriptor).use { fis ->
                            fis.readBytes().decodeToString()
                        }
                    } ?: return@launch

                    val json = Json { ignoreUnknownKeys = true }
                    val whitelistData = json.decodeFromString<List<Map<String, String>>>(jsonString)

                    whitelistData.forEach { data ->
                        val device = WhitelistedDevice(
                            deviceAddress = data["deviceAddress"] ?: return@forEach,
                            deviceType = com.burnerphone.detector.data.models.DeviceType.valueOf(
                                data["deviceType"] ?: "WIFI_NETWORK"
                            ),
                            deviceName = data["deviceName"]?.takeIf { it.isNotBlank() },
                            reason = data["reason"]?.takeIf { it.isNotBlank() },
                            notes = data["notes"]?.takeIf { it.isNotBlank() },
                            whitelistedAt = data["whitelistedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
                        )
                        app.database.whitelistedDeviceDao().insert(device)
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whitelisted Devices") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add Device")
                    }
                    IconButton(onClick = { showImportExportMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More Options")
                    }

                    DropdownMenu(
                        expanded = showImportExportMenu,
                        onDismissRequest = { showImportExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Whitelist") },
                            onClick = {
                                showImportExportMenu = false
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                exportLauncher.launch("burnerphone_whitelist_$timestamp.json")
                            },
                            leadingIcon = { Icon(Icons.Default.Share, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Whitelist") },
                            onClick = {
                                showImportExportMenu = false
                                importLauncher.launch(arrayOf("application/json"))
                            },
                            leadingIcon = { Icon(Icons.Default.Add, null) }
                        )
                        if (whitelistedDevices.isNotEmpty()) {
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Clear All") },
                                onClick = {
                                    showImportExportMenu = false
                                    showClearAllDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (whitelistedDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No whitelisted devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap + to add a device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "${whitelistedDevices.size} device${if (whitelistedDevices.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(whitelistedDevices) { device ->
                        WhitelistedDeviceCard(
                            device = device,
                            onDelete = { showDeleteDialog = device },
                            onClick = { onDeviceClick(device.deviceAddress) }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { device ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Remove from Whitelist?") },
            text = {
                Column {
                    Text("Device: ${device.deviceName ?: device.deviceAddress}")
                    Text(
                        "This device will be included in anomaly detection again.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.database.whitelistedDeviceDao().delete(device)
                            showDeleteDialog = null
                        }
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear all confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Whitelisted Devices?") },
            text = {
                Text("This will remove all ${whitelistedDevices.size} devices from the whitelist. They will be included in anomaly detection again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            whitelistedDevices.forEach { device ->
                                app.database.whitelistedDeviceDao().delete(device)
                            }
                            showClearAllDialog = false
                        }
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add device dialog
    if (showAddDialog) {
        AddDeviceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { address, name, deviceType, reason ->
                scope.launch {
                    val device = WhitelistedDevice(
                        deviceAddress = address,
                        deviceType = deviceType,
                        deviceName = name.takeIf { it.isNotBlank() },
                        reason = reason.takeIf { it.isNotBlank() }
                    )
                    app.database.whitelistedDeviceDao().insert(device)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun WhitelistedDeviceCard(
    device: WhitelistedDevice,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = device.deviceName ?: "Unknown Device",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = device.deviceAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = device.deviceType.name.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (device.reason != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reason: ${device.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Added: ${formatTimestamp(device.whitelistedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Remove from whitelist")
            }
        }
    }
}

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, com.burnerphone.detector.data.models.DeviceType, String) -> Unit
) {
    var deviceAddress by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf(com.burnerphone.detector.data.models.DeviceType.WIFI_NETWORK) }
    var reason by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Device to Whitelist") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = deviceAddress,
                    onValueChange = { deviceAddress = it },
                    label = { Text("MAC Address *") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Device Name") },
                    placeholder = { Text("My Router") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = deviceType.name.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Device Type") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        com.burnerphone.detector.data.models.DeviceType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.replace("_", " ")) },
                                onClick = {
                                    deviceType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    placeholder = { Text("Known safe device") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "* Required field",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(deviceAddress, deviceName, deviceType, reason) },
                enabled = deviceAddress.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
