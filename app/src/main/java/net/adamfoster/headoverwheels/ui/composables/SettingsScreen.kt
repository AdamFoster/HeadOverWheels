package net.adamfoster.headoverwheels.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import net.adamfoster.headoverwheels.ui.theme.HeadOverWheelsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onStartScan: () -> Unit,
    hrStatus: String,
    radarStatus: String
) {
    HeadOverWheelsTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sensor Settings") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                        text = "Connect Sensors",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    SensorConnectionRow(
                        name = "Heart Rate Monitor",
                        status = hrStatus
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SensorConnectionRow(
                        name = "Radar",
                        status = radarStatus
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(onClick = onStartScan) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Restart Scan")
                    }
                }
            }
        }
    }
}

@Composable
fun SensorConnectionRow(name: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(text = "Status: $status", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
