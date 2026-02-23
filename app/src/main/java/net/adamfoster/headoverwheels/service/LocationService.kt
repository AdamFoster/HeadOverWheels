package net.adamfoster.headoverwheels.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import android.os.Looper
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import net.adamfoster.headoverwheels.data.RideData
import net.adamfoster.headoverwheels.data.RideRepository
import net.adamfoster.headoverwheels.db.RideDatabase

class LocationService : Service() {

    companion object {
        const val CHANNEL_ID = "RideServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_RIDE = "net.adamfoster.headoverwheels.action.START_RIDE"
        const val ACTION_PAUSE_RIDE = "net.adamfoster.headoverwheels.action.PAUSE_RIDE"
        const val ACTION_RESET_RIDE = "net.adamfoster.headoverwheels.action.RESET_RIDE"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: android.location.Location? = null
    private val inclineCalculator = InclineCalculator()
    private val elevationCalculator = ElevationCalculator()

    // Ride State
    private var isRecording = false
    private var totalDistance = 0.0 // in meters
    private var totalElevationGain = 0.0 // in meters
    private var totalElevationLoss = 0.0 // in meters
    private var startTime = 0L
    private var elapsedTimeOffset = 0L // Time accumulated from previous segments

    // DB
    private lateinit var db: RideDatabase
    private val scope = CoroutineScope(Dispatchers.IO)
    private val repository = RideRepository
    private lateinit var rideStateStore: RideStateStore

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Notification will be started when ride starts

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = RideDatabase.getDatabase(this)

        rideStateStore = RideStateStore(this)

        val pending = rideStateStore.loadPendingRide()
        if (pending != null) {
            totalDistance = pending.distance
            totalElevationGain = pending.gain
            totalElevationLoss = pending.loss
            elapsedTimeOffset = pending.elapsedMs
            // isRecording stays false — always restore to paused state regardless of recording state at death
            repository.updateDistance(totalDistance)
            repository.updateElevationGainLoss(totalElevationGain, totalElevationLoss)
            repository.updateElapsedTime(elapsedTimeOffset)
            repository.setHasPendingRecovery(true)
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                val status = if (availability.isLocationAvailable) "Fixed" else "Searching..."
                repository.updateGpsStatus(status)
            }

            override fun onLocationResult(locationResult: LocationResult) {
                repository.updateGpsStatus("Fixed")
                
                for (location in locationResult.locations){
                    var currentSpeed = location.speed
                    
                    // Fallback: Calculate speed if missing
                    if (!location.hasSpeed() && lastLocation != null) {
                        val distanceMeters = location.distanceTo(lastLocation!!)
                        val timeDeltaSeconds = (location.time - lastLocation!!.time) / 1000.0
                        if (timeDeltaSeconds > 0) {
                            currentSpeed = (distanceMeters / timeDeltaSeconds).toFloat()
                        }
                    }

                    // Distance and Elevation Gain/Loss Calculation (only if recording)
                    if (isRecording && lastLocation != null) {
                        totalDistance += lastLocation!!.distanceTo(location)
                        val elevDelta = elevationCalculator.update(location.altitude)
                        totalElevationGain += elevDelta.gain
                        totalElevationLoss += elevDelta.loss
                        rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
                    }

                    // Incline Calculation
                    val currentIncline = inclineCalculator.calculateIncline(location)

                    // Update Repository
                    repository.updateLocationMetrics(currentSpeed, location.altitude, currentIncline)
                    repository.updateDistance(totalDistance)
                    repository.updateElevationGainLoss(totalElevationGain, totalElevationLoss)

                    // DB Insertion (only if recording)
                    if (isRecording) {
                        scope.launch {
                            db.rideDao().insert(
                                RideData(
                                    timestamp = System.currentTimeMillis(),
                                    speed = currentSpeed,
                                    altitude = location.altitude,
                                    distance = totalDistance / 1000.0,
                                    heartRate = repository.heartRate.value 
                                )
                            )
                        }
                    }

                    lastLocation = location
                }
            }
        }

        // Timer Loop — suspends completely when not recording via flatMapLatest
        scope.launch(Dispatchers.Default) {
            repository.isRecording
                .flatMapLatest { recording ->
                    if (recording) flow { while (true) { emit(Unit); delay(1000) } }
                    else emptyFlow()
                }
                .collect { repository.updateElapsedTime(getElapsedTime()) }
        }

        startLocationUpdates()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Ride Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Head Over Wheels")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RIDE -> {
                startForeground(NOTIFICATION_ID, createNotification("Recording Ride..."))
                startRide()
            }
            ACTION_PAUSE_RIDE -> {
                pauseRide()
                updateNotification("Ride Paused")
            }
            ACTION_RESET_RIDE -> {
                resetRide()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
        return START_STICKY
    }

    private fun startRide() {
        if (!isRecording) {
            isRecording = true
            startTime = System.currentTimeMillis()
            rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
            repository.updateRecordingStatus(true)
        }
    }

    private fun pauseRide() {
        if (isRecording) {
            isRecording = false
            elapsedTimeOffset += System.currentTimeMillis() - startTime
            rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
            repository.updateRecordingStatus(false)
            elevationCalculator.reset()
        }
    }

    private fun resetRide() {
        isRecording = false
        startTime = 0L
        elapsedTimeOffset = 0L
        totalDistance = 0.0
        totalElevationGain = 0.0
        totalElevationLoss = 0.0
        inclineCalculator.reset()
        elevationCalculator.reset()
        rideStateStore.clearPendingRide()
        repository.resetRide()
    }
    
    private fun getElapsedTime(): Long {
        if (isRecording) {
            return elapsedTimeOffset + (System.currentTimeMillis() - startTime)
        }
        return elapsedTimeOffset
    }

    private var isLocationUpdatesActive = false

    private fun startLocationUpdates() {
        if (isLocationUpdatesActive) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("LocationService", "Location permission not granted")
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        isLocationUpdatesActive = true
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesActive = false
        scope.cancel()
    }
}
