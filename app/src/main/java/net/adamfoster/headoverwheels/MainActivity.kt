package net.adamfoster.headoverwheels

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.adamfoster.headoverwheels.service.LocationService
import net.adamfoster.headoverwheels.service.BleService
import net.adamfoster.headoverwheels.ui.MainViewModel
import net.adamfoster.headoverwheels.ui.SettingsViewModel
import net.adamfoster.headoverwheels.ui.composables.MainScreen
import net.adamfoster.headoverwheels.ui.composables.SettingsScreen

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (locationGranted) {
                startLocationService()
            }
            val bluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: true
            val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: true
            
            if (bluetoothScanGranted && bluetoothConnectGranted) {
                startBleService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkPermissions()

        setContent {
            val navController = rememberNavController()
            val mainViewModel: MainViewModel = viewModel()
            val mainUiState by mainViewModel.uiState.collectAsState()

            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    MainScreen(
                        speed = mainUiState.speed,
                        altitude = mainUiState.altitude,
                        distance = mainUiState.distance,
                        incline = mainUiState.incline,
                        elapsedTime = mainUiState.elapsedTime,
                        heartRate = mainUiState.heartRate,
                        hrSensorStatus = mainUiState.hrSensorStatus,
                        radarSensorStatus = mainUiState.radarSensorStatus,
                        radarDistance = mainUiState.radarDistance,
                        radarDistanceRaw = mainUiState.radarDistanceRaw,
                        isRadarConnected = mainUiState.isRadarConnected,
                        gpsStatus = mainUiState.gpsStatus,
                        isRecording = mainUiState.isRecording,
                        speedData = mainUiState.speedData,
                        elevationData = mainUiState.elevationData,
                        onToggleRide = { toggleRide(mainUiState.isRecording) },
                        onResetRide = { resetRide() },
                        onNavigateSettings = { navController.navigate("settings") }
                    )
                }
                composable("settings") {
                    val settingsViewModel: SettingsViewModel = viewModel()
                    val settingsUiState by settingsViewModel.uiState.collectAsState()
                    
                    // Trigger scan when entering settings if empty? 
                    // Or let user press scan. The button is there.
                    
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onStartScan = { settingsViewModel.startScan() },
                        onConnectDevice = { device -> settingsViewModel.connectDevice(device) },
                        onDisconnectDevice = { type -> settingsViewModel.disconnectDevice(type) },
                        scannedDevices = settingsUiState.scannedDevices,
                        hrStatus = settingsUiState.hrStatus,
                        radarStatus = settingsUiState.radarStatus,
                        targetHrAddress = settingsUiState.targetHrAddress,
                        targetRadarAddress = settingsUiState.targetRadarAddress
                    )
                }
            }
        }
    }
    
    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startLocationService()
            startBleService()
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        startService(intent)
    }

    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        startService(intent)
    }
    
    private fun toggleRide(isRecording: Boolean) {
        val action = if (isRecording) LocationService.ACTION_PAUSE_RIDE else LocationService.ACTION_START_RIDE
        
        val locIntent = Intent(this, LocationService::class.java)
        locIntent.action = action
        startService(locIntent)
        
        // Notify BleService of ride state for foreground management
        if (action == LocationService.ACTION_START_RIDE) {
            val bleIntent = Intent(this, BleService::class.java)
            bleIntent.action = BleService.ACTION_START_RIDE
            startService(bleIntent)
        }
    }

    private fun resetRide() {
        val locIntent = Intent(this, LocationService::class.java)
        locIntent.action = LocationService.ACTION_RESET_RIDE
        startService(locIntent)

        val bleIntent = Intent(this, BleService::class.java)
        bleIntent.action = BleService.ACTION_RESET_RIDE
        startService(bleIntent)
    }
}