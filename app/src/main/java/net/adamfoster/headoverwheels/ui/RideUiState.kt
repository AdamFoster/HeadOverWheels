package net.adamfoster.headoverwheels.ui

import com.github.mikephil.charting.data.Entry

data class RideUiState(
    val speed: String = "0.0 km/h",
    val altitude: String = "0 m",
    val distance: String = "0.0 km",
    val incline: String = "0.0 %",
    val elapsedTime: String = "00:00:00",
    val heartRate: String = "---",
    val radarDistance: String = "---",
    
    val gpsStatus: String = "Acquiring...",
    val hrSensorStatus: String = "disconnected",
    val radarSensorStatus: String = "disconnected",
    
    val isRecording: Boolean = false,
    val isRadarConnected: Boolean = false,
    
    // Chart Data
    val speedData: List<Entry> = emptyList(),
    val elevationData: List<Entry> = emptyList()
)
