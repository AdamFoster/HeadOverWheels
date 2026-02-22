package net.adamfoster.headoverwheels.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.adamfoster.headoverwheels.data.RideRepository
import net.adamfoster.headoverwheels.ui.theme.HeadOverWheelsTheme
import com.github.mikephil.charting.data.Entry

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    speed: String,
    altitude: String,
    elevationGain: String = "0",
    elevationLoss: String = "0",
    distance: String,
    incline: String,
    elapsedTime: String,
    heartRate: String,
    hrSensorStatus: String,
    radarSensorStatus: String,
    radarDistance: String = "---",
    radarDistanceRaw: Int = -1,
    isRadarConnected: Boolean = false,
    gpsStatus: String = "Unknown",
    isRecording: Boolean = false,
    speedData: List<Entry> = emptyList(),
    elevationData: List<Entry> = emptyList(),
    startingElevation: Float? = null,
    themeMode: RideRepository.ThemeMode = RideRepository.ThemeMode.SYSTEM,
    onToggleRide: () -> Unit = {},
    onResetRide: () -> Unit = {},
    onNavigateSettings: () -> Unit = {}
) {
    val isDarkTheme = when (themeMode) {
        RideRepository.ThemeMode.DARK -> true
        RideRepository.ThemeMode.LIGHT -> false
        RideRepository.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    Scaffold { innerPadding ->
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
                    Column {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { MetricTile(label = "Total Distance", value = distance) }
                            item { MetricTile(label = "Elapsed Time", value = elapsedTime) }
                            item { MetricTile(label = "Speed", value = speed) }
                            item { ElevationTile(altitude = altitude, elevationGain = elevationGain, elevationLoss = elevationLoss) }
                            item { MetricTile(label = "Incline", value = incline) }
                            item { MetricTile(label = "Heart Rate", value = heartRate) }
                            if (isRadarConnected) {
                                item {
                                    val radarColor = when {
                                        radarDistanceRaw in 0 until 80 -> Color.Red
                                        radarDistanceRaw >= 80 -> Color(0xFFFFC107) // Yellow
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    val contentColor = when {
                                        radarColor == Color.Red -> Color.White
                                        radarColor == Color(0xFFFFC107) -> Color.Black
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    MetricTile(
                                        label = "Vehicle Distance",
                                        value = radarDistance,
                                        containerColor = radarColor,
                                        contentColor = contentColor
                                    )
                                }
                            }
                        }
                        
                        RideChart(
                            speedData = speedData,
                            elevationData = elevationData,
                            startingElevation = startingElevation,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Control Section
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    }

                    IconButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIcon(
                        icon = Icons.Filled.LocationOn,
                        status = gpsStatus,
                        activeCondition = { it == "Fixed" },
                        warningCondition = { it == "Acquiring..." }
                    )
                    StatusIcon(
                        icon = Icons.Filled.Favorite,
                        status = hrSensorStatus,
                        activeCondition = { it == "connected" || it == "active" }
                    )
                    StatusIcon(
                        icon = Icons.Filled.Info,
                        status = radarSensorStatus,
                        activeCondition = { it == "active" || it == "connected" }
                    )
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val (displayValue, displayUnit) = splitValueAndUnit(value)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    if (displayUnit.isNotEmpty()) {
                        Text(
                            text = " $displayUnit",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ElevationTile(
    altitude: String,
    elevationGain: String,
    elevationLoss: String,
    modifier: Modifier = Modifier
) {
    var showGainLoss by remember { mutableStateOf(false) }
    val (displayValue, displayUnit) = splitValueAndUnit(altitude)
    val defaultColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clickable { showGainLoss = !showGainLoss },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = defaultColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Tap to toggle",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.TopEnd)
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Elevation",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showGainLoss) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.Green)) { append("+$elevationGain") }
                                    withStyle(SpanStyle(color = defaultColor)) { append(" m") }
                                },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.Red)) { append("-$elevationLoss") }
                                    withStyle(SpanStyle(color = defaultColor)) { append(" m") }
                                },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.displaySmall,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            if (displayUnit.isNotEmpty()) {
                                Text(
                                    text = " $displayUnit",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun splitValueAndUnit(fullValue: String): Pair<String, String> {
    val lastSpaceIndex = fullValue.lastIndexOf(' ')
    return if (lastSpaceIndex != -1) {
        val valuePart = fullValue.substring(0, lastSpaceIndex).trim()
        val unitPart = fullValue.substring(lastSpaceIndex + 1).trim()
        Pair(valuePart, unitPart)
    } else {
        Pair(fullValue, "")
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
            elevationGain = "123",
            elevationLoss = "150",
            distance = "45.6 km",
            incline = "1.5 %",
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
