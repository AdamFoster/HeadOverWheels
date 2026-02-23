package net.adamfoster.headoverwheels.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton repository acting as the Single Source of Truth for the ride data.
 * Updates are received from Services (Location, BLE) and observed by the ViewModel.
 */
object RideRepository {

    // Metric States
    private val _speed = MutableStateFlow(0f) // m/s
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _altitude = MutableStateFlow(0.0) // meters
    val altitude: StateFlow<Double> = _altitude.asStateFlow()

    private val _incline = MutableStateFlow(0.0) // percentage
    val incline: StateFlow<Double> = _incline.asStateFlow()

    private val _distance = MutableStateFlow(0.0) // meters
    val distance: StateFlow<Double> = _distance.asStateFlow()

    private val _elevationGain = MutableStateFlow(0.0) // meters
    val elevationGain: StateFlow<Double> = _elevationGain.asStateFlow()

    private val _elevationLoss = MutableStateFlow(0.0) // meters
    val elevationLoss: StateFlow<Double> = _elevationLoss.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L) // milliseconds
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _heartRate = MutableStateFlow(0) // bpm
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _radarDistance = MutableStateFlow(-1) // meters, -1 is no vehicle
    val radarDistance: StateFlow<Int> = _radarDistance.asStateFlow()

    // Status States
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _gpsStatus = MutableStateFlow("Acquiring...")
    val gpsStatus: StateFlow<String> = _gpsStatus.asStateFlow()

    private val _hrSensorStatus = MutableStateFlow("disconnected")
    val hrSensorStatus: StateFlow<String> = _hrSensorStatus.asStateFlow()

    private val _radarSensorStatus = MutableStateFlow("disconnected")
    val radarSensorStatus: StateFlow<String> = _radarSensorStatus.asStateFlow()

    private val _isRadarConnected = MutableStateFlow(false)
    val isRadarConnected: StateFlow<Boolean> = _isRadarConnected.asStateFlow()
    
    // Scan & Connection Management
    data class ScannedDevice(val name: String, val address: String, val rssi: Int, val deviceType: DeviceType)
    enum class DeviceType { HR, RADAR, UNKNOWN }

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()
    
    private val _targetHrAddress = MutableStateFlow<String?>(null)
    val targetHrAddress: StateFlow<String?> = _targetHrAddress.asStateFlow()
    
    private val _targetRadarAddress = MutableStateFlow<String?>(null)
    val targetRadarAddress: StateFlow<String?> = _targetRadarAddress.asStateFlow()

    // App Settings
    enum class ThemeMode { SYSTEM, LIGHT, DARK }
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _hasPendingRecovery = MutableStateFlow(false)
    val hasPendingRecovery: StateFlow<Boolean> = _hasPendingRecovery.asStateFlow()
    fun setHasPendingRecovery(value: Boolean) { _hasPendingRecovery.value = value }

    // Update methods called by Services

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun addScannedDevice(device: ScannedDevice) {
        _scannedDevices.update { currentList ->
            val mutableList = currentList.toMutableList()
            val existingIndex = mutableList.indexOfFirst { it.address == device.address }
            if (existingIndex != -1) {
                mutableList[existingIndex] = device
            } else {
                mutableList.add(device)
            }
            mutableList
        }
    }

    fun clearScannedDevices() {
        _scannedDevices.value = emptyList()
    }
    
    fun setTargetHrDevice(address: String?) {
        _targetHrAddress.value = address
    }

    fun setTargetRadarDevice(address: String?) {
        _targetRadarAddress.value = address
    }

    fun updateLocationMetrics(speed: Float, altitude: Double, incline: Double) {
        _speed.value = speed
        _altitude.value = altitude
        _incline.value = incline
    }

    fun updateDistance(totalDistance: Double) {
        _distance.value = totalDistance
    }

    fun updateElevationGainLoss(gain: Double, loss: Double) {
        _elevationGain.value = gain
        _elevationLoss.value = loss
    }

    fun updateElapsedTime(timeMs: Long) {
        _elapsedTime.value = timeMs
    }

    fun updateHeartRate(hr: Int) {
        _heartRate.value = hr
    }

    fun updateRadarDistance(dist: Int) {
        _radarDistance.value = dist
    }

    fun updateRecordingStatus(recording: Boolean) {
        _isRecording.value = recording
    }

    fun updateGpsStatus(status: String) {
        _gpsStatus.value = status
    }

    fun updateHrSensorStatus(status: String) {
        _hrSensorStatus.value = status
    }

    fun updateRadarSensorStatus(status: String, isConnected: Boolean) {
        _radarSensorStatus.value = status
        _isRadarConnected.value = isConnected
    }

    fun resetRide() {
        _speed.value = 0f
        _altitude.value = 0.0
        _incline.value = 0.0
        _distance.value = 0.0
        _elevationGain.value = 0.0
        _elevationLoss.value = 0.0
        _elapsedTime.value = 0L
        _heartRate.value = 0
        _radarDistance.value = -1
        _isRecording.value = false
        _hasPendingRecovery.value = false
        // Don't reset sensor connection statuses
    }

    fun resetAll() {
        resetRide()
        _gpsStatus.value = "Acquiring..."
        _hrSensorStatus.value = "disconnected"
        _radarSensorStatus.value = "disconnected"
        _isRadarConnected.value = false
        _scannedDevices.value = emptyList()
        _targetHrAddress.value = null
        _targetRadarAddress.value = null
        _themeMode.value = ThemeMode.SYSTEM
    }
}
