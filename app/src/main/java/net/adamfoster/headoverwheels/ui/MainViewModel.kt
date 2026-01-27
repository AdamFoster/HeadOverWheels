package net.adamfoster.headoverwheels.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.adamfoster.headoverwheels.data.RideRepository
import java.util.Locale
import kotlin.math.roundToInt

class MainViewModel : ViewModel() {
    private val repository = RideRepository

    private val _speedData = MutableStateFlow<List<Entry>>(emptyList())
    private val _elevationData = MutableStateFlow<List<Entry>>(emptyList())
    private var dataPointIndex = 0f

    // Chunk 1: Primary Metrics
    private val metricsFlow = combine(
        repository.speed,
        repository.altitude,
        repository.distance,
        repository.incline,
        repository.elapsedTime
    ) { speed, altitude, distance, incline, elapsedTime ->
        MetricsChunk(speed, altitude, distance, incline, elapsedTime)
    }

    // Chunk 2: Sensor Data
    private val sensorsFlow = combine(
        repository.heartRate,
        repository.radarDistance,
        repository.hrSensorStatus,
        repository.radarSensorStatus
    ) { heartRate, radarDistance, hrStatus, radarStatus ->
        SensorChunk(heartRate, radarDistance, hrStatus, radarStatus)
    }

    // Chunk 3: System Status
    private val statusFlow = combine(
        repository.gpsStatus,
        repository.isRecording,
        repository.isRadarConnected
    ) { gps, recording, radarConn ->
        StatusChunk(gps, recording, radarConn)
    }

    val uiState: StateFlow<RideUiState> = combine(
        metricsFlow,
        sensorsFlow,
        statusFlow,
        _speedData,
        _elevationData
    ) { metrics, sensors, status, speedData, elevData ->
        
        val speedKmh = metrics.speed * 3.6f

        RideUiState(
            speed = "${speedKmh.roundToInt()} km/h",
            altitude = "${metrics.altitude.roundToInt()} m",
            distance = String.format(Locale.getDefault(), "%.1f km", metrics.distance / 1000.0),
            incline = String.format(Locale.getDefault(), "%.1f %%", metrics.incline),
            elapsedTime = formatElapsedTime(metrics.elapsedTime),
            heartRate = if (sensors.heartRate > 0) "${sensors.heartRate} bpm" else "---",
            radarDistance = if (sensors.radarDistance >= 0) "${sensors.radarDistance} m" else "---",
            radarDistanceRaw = sensors.radarDistance,
            gpsStatus = status.gpsStatus,
            hrSensorStatus = sensors.hrSensorStatus,
            radarSensorStatus = sensors.radarSensorStatus,
            isRecording = status.isRecording,
            isRadarConnected = status.isRadarConnected,
            speedData = speedData,
            elevationData = elevData
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RideUiState()
    )

    init {
        viewModelScope.launch {
             repository.speed.collect { speed ->
                 val altitude = repository.altitude.value
                 val speedKmh = speed * 3.6f
                 
                 val currentSpeedList = _speedData.value.toMutableList()
                 val currentElevList = _elevationData.value.toMutableList()
            
                 currentSpeedList.add(Entry(dataPointIndex, speedKmh))
                 currentElevList.add(Entry(dataPointIndex, altitude.toFloat()))
            
                 if (currentSpeedList.size > 300) {
                     currentSpeedList.removeAt(0)
                     currentElevList.removeAt(0)
                 }
            
                 _speedData.value = currentSpeedList
                 _elevationData.value = currentElevList
                 dataPointIndex += 1f
             }
        }
        
        viewModelScope.launch {
            repository.distance.collect { distance ->
                 if (distance == 0.0) {
                     _speedData.value = emptyList()
                     _elevationData.value = emptyList()
                     dataPointIndex = 0f
                 }
            }
        }
    }

    private fun formatElapsedTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

private data class MetricsChunk(
    val speed: Float,
    val altitude: Double,
    val distance: Double,
    val incline: Double,
    val elapsedTime: Long
)

private data class SensorChunk(
    val heartRate: Int,
    val radarDistance: Int,
    val hrSensorStatus: String,
    val radarSensorStatus: String
)

private data class StatusChunk(
    val gpsStatus: String,
    val isRecording: Boolean,
    val isRadarConnected: Boolean
)