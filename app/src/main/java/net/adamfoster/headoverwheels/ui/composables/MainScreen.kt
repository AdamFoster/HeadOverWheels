package net.adamfoster.headoverwheels.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.adamfoster.headoverwheels.ui.theme.HeadOverWheelsTheme
import com.github.mikephil.charting.data.Entry

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    speed: String,
    altitude: String,
    distance: String,
    elapsedTime: String,
    heartRate: String,
    hrSensorStatus: String,
    radarSensorStatus: String,
    radarDistance: String = "---",
    isRadarConnected: Boolean = false,
    gpsStatus: String = "Unknown",
    isRecording: Boolean = false,
    speedData: List<Entry> = emptyList(),
    elevationData: List<Entry> = emptyList(),
    onToggleRide: () -> Unit = {},
    onResetRide: () -> Unit = {},
    onNavigateSettings: () -> Unit = {}
) {
    HeadOverWheelsTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Head Over Wheels") },
                    actions = {
                        IconButton(onClick = onNavigateSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                    // 1. Grid Section (Weighted)
                    Box(modifier = Modifier.weight(2f)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item { MetricTile(label = "Speed", value = speed) }
                            item { MetricTile(label = "Elevation", value = altitude) }
                            item { MetricTile(label = "Incline", value = "0.0 %") }
                            item { MetricTile(label = "Elapsed Time", value = elapsedTime) }
                            item { MetricTile(label = "Distance", value = distance) }
                            item { MetricTile(label = "Heart Rate", value = heartRate) }
                            if (isRadarConnected) {
                                item { MetricTile(label = "Vehicle Dist", value = radarDistance) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Chart Section (Weighted)
                    Box(modifier = Modifier.weight(1f)) {
                        RideChart(
                            speedData = speedData,
                            elevationData = elevationData,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Control Button (Fixed Size)
                    val haptic = LocalHapticFeedback.current
                    val buttonColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    ) {
                        Surface(
                            color = buttonColor,
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleRide()
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onResetRide()
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isRecording) "STOP" else "START",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Hold to Reset",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Status Icons (Fixed Size)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusIcon(
                            icon = Icons.Default.LocationOn,
                            status = gpsStatus,
                            activeCondition = { it == "Fixed" },
                            warningCondition = { it == "Acquiring..." }
                        )
                        StatusIcon(
                            icon = Icons.Default.Favorite,
                            status = hrSensorStatus,
                            activeCondition = { it == "connected" }
                        )
                        StatusIcon(
                            icon = Icons.Default.Info,
                            status = radarSensorStatus,
                            activeCondition = { it == "Active" || it == "connected" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.3f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusIcon(
    icon: ImageVector,
    status: String,
    activeCondition: (String) -> Boolean,
    warningCondition: (String) -> Boolean = { false }
) {
    val tint = when {
        activeCondition(status) -> Color.Green
        warningCondition(status) -> Color(0xFFFFC107) // Yellow
        else -> Color.Gray
    }
    
    Icon(
        imageVector = icon,
        contentDescription = status,
        tint = tint,
        modifier = Modifier.size(24.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HeadOverWheelsTheme {
        MainScreen(
            speed = "12.3 km/h",
            altitude = "123 m",
            distance = "45.6 km",
            elapsedTime = "01:23:45",
            heartRate = "120",
            hrSensorStatus = "connected",
            radarSensorStatus = "connected",
            radarDistance = "15 m",
            isRadarConnected = true,
            gpsStatus = "Fixed",
            isRecording = false
        )
    }
}