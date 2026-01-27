package net.adamfoster.headoverwheels.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.adamfoster.headoverwheels.data.RideRepository
import net.adamfoster.headoverwheels.ui.theme.HeadOverWheelsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onStartScan: () -> Unit,
    onConnectDevice: (RideRepository.ScannedDevice) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    scannedDevices: List<RideRepository.ScannedDevice>,
    hrStatus: String,
    radarStatus: String,
    targetHrAddress: String?,
    targetRadarAddress: String?
) {
    HeadOverWheelsTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sensor Settings") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onStartScan) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Scan")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connected Sensors",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    SensorStatusCard(
                        title = "Heart Rate Monitor",
                        status = hrStatus,
                        address = targetHrAddress,
                        onDisconnect = { onDisconnectDevice("HR") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SensorStatusCard(
                        title = "Radar",
                        status = radarStatus,
                        address = targetRadarAddress,
                        onDisconnect = { onDisconnectDevice("RADAR") }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Available Devices",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (scannedDevices.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning...")
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(scannedDevices) { device ->
                                ScannedDeviceItem(
                                    device = device,
                                    onConnect = { onConnectDevice(device) },
                                    isConnected = device.address == targetHrAddress || device.address == targetRadarAddress
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorStatusCard(
    title: String, 
    status: String, 
    address: String?,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Status: ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status == "connected" || status == "active") Color.Green else Color.Gray
                    )
                }
                if (address != null) {
                    Text(text = "Address: $address", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (status == "connected" || address != null) {
                Button(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
fun ScannedDeviceItem(
    device: RideRepository.ScannedDevice,
    onConnect: () -> Unit,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnected, onClick = onConnect)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = "${device.address} (${device.rssi} dBm)", style = MaterialTheme.typography.bodySmall)
                Text(text = "Type: ${device.deviceType}", style = MaterialTheme.typography.labelSmall)
            }
            if (isConnected) {
                Text("Connected", color = Color.Green)
            } else {
                Button(onClick = onConnect) {
                    Text("Connect")
                }
            }
        }
    }
}
