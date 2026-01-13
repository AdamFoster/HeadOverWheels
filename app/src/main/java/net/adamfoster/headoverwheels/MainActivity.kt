package net.adamfoster.headoverwheels

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.adamfoster.headoverwheels.ui.composables.MainScreen
import net.adamfoster.headoverwheels.ui.composables.SettingsScreen
import net.adamfoster.headoverwheels.service.LocationService
import java.util.Locale
import kotlin.math.roundToInt
import com.github.mikephil.charting.data.Entry

class MainActivity : ComponentActivity() {

    private val speed = mutableStateOf("0.0 km/h")
    private val altitude = mutableStateOf("0 m")
    private val distance = mutableStateOf("0.0 km")
    private val elapsedTime = mutableStateOf("00:00:00")
    private val heartRate = mutableStateOf("---")
    private val hrSensorStatus = mutableStateOf("disconnected")
    private val radarSensorStatus = mutableStateOf("disconnected")
    private val radarDistance = mutableStateOf("---")
    private val isRadarConnected = mutableStateOf(false)
    private val gpsStatus = mutableStateOf("Acquiring...")
    private val isRecording = mutableStateOf(false)
    
    // Chart Data
    private val speedData = mutableStateOf(listOf<Entry>())
    private val elevationData = mutableStateOf(listOf<Entry>())
    private var dataPointIndex = 0f

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

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val newSpeed = intent?.getFloatExtra("speed", 0f) ?: 0f
            val newAltitude = intent?.getDoubleExtra("altitude", 0.0) ?: 0.0
            val totalDistanceMeters = intent?.getDoubleExtra("total_distance", 0.0) ?: 0.0
            
            val speedKmh = (newSpeed * 3.6f)
            
            speed.value = "${speedKmh.roundToInt()} km/h"
            altitude.value = "${newAltitude.roundToInt()} m"
            distance.value = String.format(Locale.getDefault(), "%.1f km", totalDistanceMeters / 1000.0)
            
            // Append chart data
            // Note: In a real app, optimize this to not reconstruct the list every second
            // For this prototype, it's acceptable.
            val currentSpeedList = speedData.value.toMutableList()
            val currentElevList = elevationData.value.toMutableList()
            
            currentSpeedList.add(Entry(dataPointIndex, speedKmh))
            currentElevList.add(Entry(dataPointIndex, newAltitude.toFloat()))
            
            // Limit to last 300 points to prevent memory issues
            if (currentSpeedList.size > 300) {
                currentSpeedList.removeAt(0)
                currentElevList.removeAt(0)
            }
            
            speedData.value = currentSpeedList
            elevationData.value = currentElevList
            dataPointIndex += 1f
        }
    }
    
    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val millis = intent?.getLongExtra("elapsed_time_ms", 0L) ?: 0L
            val seconds = (millis / 1000) % 60
            val minutes = (millis / (1000 * 60)) % 60
            val hours = (millis / (1000 * 60 * 60)) % 24
            elapsedTime.value = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isRecording.value = intent?.getBooleanExtra("is_recording", false) ?: false
        }
    }

    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val newHeartRate = intent?.getIntExtra("heart_rate", 0) ?: 0
            heartRate.value = newHeartRate.toString()
            hrSensorStatus.value = "connected"
        }
    }

    private val radarReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "radar_update") {
                val dist = intent.getIntExtra("distance", -1)
                if (dist != -1) {
                    radarDistance.value = "$dist m"
                    isRadarConnected.value = true
                    radarSensorStatus.value = "Active"
                }
            } else if (intent?.action == "radar_status") {
                val status = intent.getStringExtra("status") ?: "disconnected"
                radarSensorStatus.value = status
                if (status == "disconnected") {
                    isRadarConnected.value = false
                }
            }
        }
    }

    private val gpsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: "Unknown"
            gpsStatus.value = status
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    MainScreen(
                        speed = speed.value,
                        altitude = altitude.value,
                        distance = distance.value,
                        elapsedTime = elapsedTime.value,
                        heartRate = heartRate.value,
                        hrSensorStatus = hrSensorStatus.value,
                        radarSensorStatus = radarSensorStatus.value,
                        radarDistance = radarDistance.value,
                        isRadarConnected = isRadarConnected.value,
                        gpsStatus = gpsStatus.value,
                        isRecording = isRecording.value,
                        speedData = speedData.value,
                        elevationData = elevationData.value,
                        onToggleRide = { toggleRide() },
                        onResetRide = {
                            resetRide() 
                            // Clear chart
                            speedData.value = emptyList()
                            elevationData.value = emptyList()
                            dataPointIndex = 0f
                        },
                        onNavigateSettings = { navController.navigate("settings") }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onStartScan = { startScan() },
                        hrStatus = hrSensorStatus.value,
                        radarStatus = radarSensorStatus.value
                    )
                }
            }
        }
    }
    
    private fun startScan() {
        val intent = Intent(this, net.adamfoster.headoverwheels.service.BleService::class.java)
        intent.action = net.adamfoster.headoverwheels.service.BleService.ACTION_START_SCAN
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, locationReceiver, IntentFilter("location_update"), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, heartRateReceiver, IntentFilter("heart_rate_update"), ContextCompat.RECEIVER_NOT_EXPORTED)
        val radarFilter = IntentFilter().apply {
            addAction("radar_update")
            addAction("radar_status")
        }
        ContextCompat.registerReceiver(this, radarReceiver, radarFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, gpsStatusReceiver, IntentFilter("gps_status"), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, timerReceiver, IntentFilter("ride_timer_update"), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, statusReceiver, IntentFilter("ride_status_update"), ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Sync status on resume
        val intent = Intent(this, LocationService::class.java)
        intent.action = LocationService.ACTION_REQUEST_STATUS
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(locationReceiver)
        unregisterReceiver(heartRateReceiver)
        unregisterReceiver(radarReceiver)
        unregisterReceiver(gpsStatusReceiver)
        unregisterReceiver(timerReceiver)
        unregisterReceiver(statusReceiver)
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        startService(intent)
    }

    private fun startBleService() {
        val intent = Intent(this, net.adamfoster.headoverwheels.service.BleService::class.java)
        startService(intent)
    }
    
    private fun toggleRide() {
        val intent = Intent(this, LocationService::class.java)
        intent.action = if (isRecording.value) LocationService.ACTION_PAUSE_RIDE else LocationService.ACTION_START_RIDE
        startService(intent)
    }

    private fun resetRide() {
        val intent = Intent(this, LocationService::class.java)
        intent.action = LocationService.ACTION_RESET_RIDE
        startService(intent)
    }
}
