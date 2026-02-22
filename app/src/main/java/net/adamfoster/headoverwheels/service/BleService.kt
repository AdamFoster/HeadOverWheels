package net.adamfoster.headoverwheels.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.app.NotificationCompat
import net.adamfoster.headoverwheels.data.RideRepository
import net.adamfoster.headoverwheels.service.ble.HeartRateManager
import net.adamfoster.headoverwheels.service.ble.RadarManager

@SuppressLint("MissingPermission")
class BleService : Service() {

    companion object {
        const val CHANNEL_ID = "BleServiceChannel"
        const val NOTIFICATION_ID = 2
        const val ACTION_START_SCAN = "net.adamfoster.headoverwheels.action.START_SCAN"
        const val ACTION_START_RIDE = "net.adamfoster.headoverwheels.action.START_RIDE"
        const val ACTION_RESET_RIDE = "net.adamfoster.headoverwheels.action.RESET_RIDE"
        const val ACTION_CONNECT_DEVICE = "net.adamfoster.headoverwheels.action.CONNECT_DEVICE"
        const val ACTION_DISCONNECT_DEVICE = "net.adamfoster.headoverwheels.action.DISCONNECT_DEVICE"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_TYPE = "device_type" // "HR" or "RADAR"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    
    private lateinit var hrManager: HeartRateManager
    private lateinit var radarManager: RadarManager
    private var toneGenerator: ToneGenerator? = null

    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Notification starts on ride start

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.e("BleService", "Failed to create ToneGenerator", e)
        }

        // Initialize managers with Repository
        hrManager = HeartRateManager(RideRepository)
        radarManager = RadarManager(RideRepository) {
            // Callback for Radar Alert
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 375)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Connectivity Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Head Over Wheels Sensors")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> startScanning()
            ACTION_START_RIDE -> {
                startForeground(NOTIFICATION_ID, createNotification("Sensors Connected (Ride Active)"))
            }
            ACTION_RESET_RIDE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            ACTION_CONNECT_DEVICE -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val type = intent.getStringExtra(EXTRA_DEVICE_TYPE)
                if (address != null && type != null) {
                    connectToDevice(address, type)
                }
            }
            ACTION_DISCONNECT_DEVICE -> {
                val type = intent.getStringExtra(EXTRA_DEVICE_TYPE)
                if (type != null) {
                    disconnectDevice(type)
                }
            }
            else -> startScanning()
        }
        return START_STICKY
    }

    private fun startScanning() {
        if (isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        // Clear previous scan results
        RideRepository.clearScannedDevices()

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(HeartRateManager.HEART_RATE_SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(RadarManager.RADAR_SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanner.startScan(filters, settings, scanCallback)
        isScanning = true
        Log.d("BleService", "Started scanning for HR and Radar")
        updateNotification("Scanning for sensors...")
    }

    private fun connectToDevice(address: String, type: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        Log.i("BleService", "Initiating connection to $type at $address")
        if (isScanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }
        if (type == "HR") {
            RideRepository.setTargetHrDevice(address)
            hrManager.setGatt(device.connectGatt(this, false, hrGattCallback))
        } else if (type == "RADAR") {
            RideRepository.setTargetRadarDevice(address)
            radarManager.setGatt(device.connectGatt(this, false, radarGattCallback))
        }
    }

    private fun disconnectDevice(type: String) {
        if (type == "HR") {
            RideRepository.setTargetHrDevice(null)
            hrManager.getGatt()?.disconnect()
            // Cleanup happens in callback
        }
        if (type == "RADAR") {
            RideRepository.setTargetRadarDevice(null)
            radarManager.getGatt()?.disconnect()
            // Cleanup happens in callback
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val uuids = result.scanRecord?.serviceUuids ?: return
            val rssi = result.rssi

            // Identify and Add to Repository
            var type = RideRepository.DeviceType.UNKNOWN
            
            if (uuids.contains(ParcelUuid(HeartRateManager.HEART_RATE_SERVICE_UUID))) {
                type = RideRepository.DeviceType.HR
            } else if (uuids.contains(ParcelUuid(RadarManager.RADAR_SERVICE_UUID))) {
                type = RideRepository.DeviceType.RADAR
            }

            if (type != RideRepository.DeviceType.UNKNOWN) {
                val scannedDevice = RideRepository.ScannedDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address,
                    rssi = rssi,
                    deviceType = type
                )
                RideRepository.addScannedDevice(scannedDevice)

                // Auto-connect ONLY if it matches target
                if (type == RideRepository.DeviceType.HR && device.address == RideRepository.targetHrAddress.value) {
                    if (!hrManager.isConnected()) {
                        Log.i("BleService", "Auto-connecting to Target HR: ${device.address}")
                        connectToDevice(device.address, "HR")
                    }
                }
                
                if (type == RideRepository.DeviceType.RADAR && device.address == RideRepository.targetRadarAddress.value) {
                     if (!radarManager.isConnected()) {
                        Log.i("BleService", "Auto-connecting to Target Radar: ${device.address}")
                        connectToDevice(device.address, "RADAR")
                    }
                }
            }
        }
    }

    private val hrGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BleService", "HR connected: ${gatt.device.address}")
                gatt.discoverServices()
                hrManager.onConnected(gatt)
                updateNotification("HR Connected")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BleService", "HR disconnected.")
                hrManager.onDisconnected()
                gatt.close()
                updateNotification("HR Disconnected")
                // Only auto-scan if this was not a user-initiated disconnect.
                // disconnectDevice() clears targetHrAddress before calling gatt.disconnect(),
                // so the BLE stack delivers this callback after that write is visible.
                if (RideRepository.targetHrAddress.value != null) startScanning()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) hrManager.onServicesDiscovered(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            hrManager.onCharacteristicChanged(characteristic.uuid, value)
        }
    }

    private val radarGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BleService", "Radar connected: ${gatt.device.address}")
                gatt.discoverServices()
                radarManager.onConnected(gatt)
                updateNotification("Radar Connected")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BleService", "Radar disconnected.")
                radarManager.onDisconnected()
                gatt.close()
                updateNotification("Radar Disconnected")
                // Only auto-scan if this was not a user-initiated disconnect.
                // disconnectDevice() clears targetRadarAddress before calling gatt.disconnect(),
                // so the BLE stack delivers this callback after that write is visible.
                if (RideRepository.targetRadarAddress.value != null) startScanning()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) radarManager.onServicesDiscovered(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            radarManager.onCharacteristicChanged(characteristic.uuid, value)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        hrManager.close()
        radarManager.close()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        toneGenerator?.release()
        toneGenerator = null
    }
}
