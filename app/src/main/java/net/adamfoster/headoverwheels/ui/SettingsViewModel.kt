package net.adamfoster.headoverwheels.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.adamfoster.headoverwheels.data.RideRepository
import net.adamfoster.headoverwheels.service.BleService

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RideRepository

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.scannedDevices,
        repository.hrSensorStatus,
        repository.radarSensorStatus,
        repository.targetHrAddress,
        repository.targetRadarAddress
    ) { scannedDevices, hrStatus, radarStatus, targetHr, targetRadar ->
        SettingsUiState(
            scannedDevices = scannedDevices,
            hrStatus = hrStatus,
            radarStatus = radarStatus,
            targetHrAddress = targetHr,
            targetRadarAddress = targetRadar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun startScan() {
        val intent = Intent(getApplication(), BleService::class.java).apply {
            action = BleService.ACTION_START_SCAN
        }
        getApplication<Application>().startService(intent)
    }

    fun connectDevice(device: RideRepository.ScannedDevice) {
        val intent = Intent(getApplication(), BleService::class.java).apply {
            action = BleService.ACTION_CONNECT_DEVICE
            putExtra(BleService.EXTRA_DEVICE_ADDRESS, device.address)
            putExtra(BleService.EXTRA_DEVICE_TYPE, if (device.deviceType == RideRepository.DeviceType.HR) "HR" else "RADAR")
        }
        getApplication<Application>().startService(intent)
    }

    fun disconnectDevice(type: String) {
        val intent = Intent(getApplication(), BleService::class.java).apply {
            action = BleService.ACTION_DISCONNECT_DEVICE
            putExtra(BleService.EXTRA_DEVICE_TYPE, type)
        }
        getApplication<Application>().startService(intent)
    }
}

data class SettingsUiState(
    val scannedDevices: List<RideRepository.ScannedDevice> = emptyList(),
    val hrStatus: String = "disconnected",
    val radarStatus: String = "disconnected",
    val targetHrAddress: String? = null,
    val targetRadarAddress: String? = null
)
