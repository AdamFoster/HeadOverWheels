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
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.adamfoster.headoverwheels.data.RideData
import net.adamfoster.headoverwheels.db.RideDatabase
import java.util.Timer
import java.util.TimerTask

class LocationService : Service() {

    companion object {
        const val CHANNEL_ID = "RideServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_RIDE = "net.adamfoster.headoverwheels.action.START_RIDE"
        const val ACTION_PAUSE_RIDE = "net.adamfoster.headoverwheels.action.PAUSE_RIDE"
        const val ACTION_RESET_RIDE = "net.adamfoster.headoverwheels.action.RESET_RIDE"
        const val ACTION_REQUEST_STATUS = "net.adamfoster.headoverwheels.action.REQUEST_STATUS"
        const val INCLINE_SMOOTHING_WINDOW = 5
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: android.location.Location? = null
    private val recentLocations = java.util.ArrayDeque<android.location.Location>(INCLINE_SMOOTHING_WINDOW)

    // Ride State
    private var isRecording = false
    private var totalDistance = 0.0 // in meters
    private var startTime = 0L
    private var elapsedTimeOffset = 0L // Time accumulated from previous segments
    private var timer: Timer? = null

    // DB
    private lateinit var db: RideDatabase
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Ready to ride"))

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = RideDatabase.getDatabase(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                val status = if (availability.isLocationAvailable) "Fixed" else "Searching..."
                broadcastGpsStatus(status)
            }

            override fun onLocationResult(locationResult: LocationResult) {
                broadcastGpsStatus("Fixed")
                
                for (location in locationResult.locations){
                    var currentSpeed = location.speed
                    
                    // Fallback: Calculate speed if missing (common in Emulator)
                    if (!location.hasSpeed() && lastLocation != null) {
                        val distanceMeters = location.distanceTo(lastLocation!!)
                        val timeDeltaSeconds = (location.time - lastLocation!!.time) / 1000.0
                        if (timeDeltaSeconds > 0) {
                            currentSpeed = (distanceMeters / timeDeltaSeconds).toFloat()
                        }
                    }

                    // Distance Calculation (only if recording)
                    if (isRecording && lastLocation != null) {
                        totalDistance += lastLocation!!.distanceTo(location)
                    }

                    // Incline Calculation
                    recentLocations.addLast(location)
                    if (recentLocations.size > INCLINE_SMOOTHING_WINDOW) {
                        recentLocations.removeFirst()
                    }

                    var currentIncline = 0.0
                    if (recentLocations.size >= 2) {
                        val oldest = recentLocations.first
                        val newest = recentLocations.last
                        val dist = oldest.distanceTo(newest)
                        val altDiff = newest.altitude - oldest.altitude
                        
                        // Avoid division by zero and extremely small distances which cause noise
                        if (dist > 10.0) {
                            currentIncline = (altDiff / dist) * 100.0
                        }
                    }

                    // DB Insertion (only if recording)
                    if (isRecording) {
                        scope.launch {
                            db.rideDao().insert(
                                RideData(
                                    timestamp = System.currentTimeMillis(),
                                    speed = currentSpeed,
                                    altitude = location.altitude,
                                    distance = totalDistance / 1000.0, // Store in KM
                                    heartRate = 0 
                                )
                            )
                        }
                    }

                    lastLocation = location

                    // Broadcast location update
                    val intent = Intent("location_update")
                    intent.setPackage(packageName)
                    intent.putExtra("latitude", location.latitude)
                    intent.putExtra("longitude", location.longitude)
                    intent.putExtra("speed", currentSpeed)
                    intent.putExtra("altitude", location.altitude)
                    intent.putExtra("incline", currentIncline)
                    intent.putExtra("timestamp", System.currentTimeMillis())
                    
                    // Broadcast persistent metrics
                    intent.putExtra("total_distance", totalDistance)
                    intent.putExtra("is_recording", isRecording)
                    sendBroadcast(intent)
                }
            }
        }
        
        // Start a timer to broadcast elapsed time independent of GPS updates
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                broadcastTime()
            }
        }, 0, 1000)
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
                startRide()
                updateNotification("Recording Ride...")
            }
            ACTION_PAUSE_RIDE -> {
                pauseRide()
                updateNotification("Ride Paused")
            }
            ACTION_RESET_RIDE -> {
                resetRide()
                updateNotification("Ready to ride")
            }
            ACTION_REQUEST_STATUS -> broadcastStatus()
        }
        
        // Always ensure location updates are running
        startLocationUpdates()
        return START_STICKY
    }

    private fun startRide() {
        if (!isRecording) {
            isRecording = true
            startTime = System.currentTimeMillis()
            broadcastStatus()
        }
    }

    private fun pauseRide() {
        if (isRecording) {
            isRecording = false
            elapsedTimeOffset += System.currentTimeMillis() - startTime
            broadcastStatus()
        }
    }

    private fun resetRide() {
        isRecording = false
        startTime = 0L
        elapsedTimeOffset = 0L
        totalDistance = 0.0
        recentLocations.clear()
        broadcastStatus()
        broadcastTime() // Reset UI to 00:00:00
    }
    
    private fun getElapsedTime(): Long {
        if (isRecording) {
            return elapsedTimeOffset + (System.currentTimeMillis() - startTime)
        }
        return elapsedTimeOffset
    }

    private fun broadcastTime() {
        val intent = Intent("ride_timer_update")
        intent.setPackage(packageName)
        intent.putExtra("elapsed_time_ms", getElapsedTime())
        sendBroadcast(intent)
    }

    private fun broadcastStatus() {
        val intent = Intent("ride_status_update")
        intent.setPackage(packageName)
        intent.putExtra("is_recording", isRecording)
        sendBroadcast(intent)
    }

    private fun broadcastGpsStatus(status: String) {
        val intent = Intent("gps_status")
        intent.setPackage(packageName)
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    private fun startLocationUpdates() {
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
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timer?.cancel()
    }
}