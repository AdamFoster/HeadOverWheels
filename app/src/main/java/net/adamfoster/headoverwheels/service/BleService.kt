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
import androidx.core.app.NotificationCompat
import java.util.*

@SuppressLint("MissingPermission") // Permissions are checked in MainActivity before starting
class BleService : Service() {

    companion object {
        const val CHANNEL_ID = "BleServiceChannel"
        const val NOTIFICATION_ID = 2
        const val ACTION_START_SCAN = "net.adamfoster.headoverwheels.action.START_SCAN"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    
    // Separate GATT clients for separate devices
    private var hrGatt: BluetoothGatt? = null
    private var radarGatt: BluetoothGatt? = null

    // Heart Rate
    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    
    // Garmin Varia Radar
    private val RADAR_SERVICE_UUID = UUID.fromString("6AFF7000-56C8-4203-9068-185C32196F33")
    private val RADAR_DATA_CHAR_UUID = UUID.fromString("6AFF7101-56C8-4203-9068-185C32196F33")

    private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Ready to connect sensors"))

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
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
        if (intent?.action == ACTION_START_SCAN) {
             startScanning()
        } else {
             // Auto-start scanning on first launch if permissions allow?
             // Yes, for now.
             startScanning()
        }
        return START_STICKY
    }

    private fun startScanning() {
        if (isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        // We filter for devices advertising ANY of our target services
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(RADAR_SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanner.startScan(filters, settings, scanCallback)
        isScanning = true
        Log.d("BleService", "Started scanning for HR and Radar")
        updateNotification("Scanning for sensors...")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val uuids = result.scanRecord?.serviceUuids ?: return

            // Check for Heart Rate
            if (uuids.contains(ParcelUuid(HEART_RATE_SERVICE_UUID))) {
                if (hrGatt == null) {
                    Log.i("BleService", "Found HR Sensor: ${device.address}")
                    hrGatt = device.connectGatt(this@BleService, true, gattCallback)
                    updateNotification("Connecting HR Sensor...")
                }
            }
            
            // Check for Radar
            if (uuids.contains(ParcelUuid(RADAR_SERVICE_UUID))) {
                 if (radarGatt == null) {
                    Log.i("BleService", "Found Radar: ${device.address}")
                    radarGatt = device.connectGatt(this@BleService, true, gattCallback)
                    updateNotification("Connecting Radar...")
                }
            }
            
            // Stop scanning if both connected? 
            // For stability, keeping scanning on might be battery draining.
            // But if one disconnects, we might want to auto-reconnect.
            // For now, let's keep scanning or stop if both are found.
            if (hrGatt != null && radarGatt != null) {
                 // bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                 // isScanning = false
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BleService", "Connected to GATT server: ${gatt?.device?.address}")
                gatt?.discoverServices()
                
                if (gatt == hrGatt) {
                     updateNotification("HR Connected")
                     // Wait for services discovered to update UI
                } else if (gatt == radarGatt) {
                     updateNotification("Radar Connected")
                     broadcastUpdate("radar_status", "connected")
                }
                
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BleService", "Disconnected from GATT server.")
                if (gatt == hrGatt) {
                    hrGatt = null
                    val intent = Intent("heart_rate_update")
                    intent.putExtra("heart_rate", 0)
                    sendBroadcast(intent)
                    updateNotification("HR Disconnected")
                } else if (gatt == radarGatt) {
                    radarGatt = null
                    broadcastUpdate("radar_status", "disconnected")
                    updateNotification("Radar Disconnected")
                }
                gatt?.close()
                // Restart scanning if disconnected?
                startScanning()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Determine which services are available and subscribe
                val hrService = gatt?.getService(HEART_RATE_SERVICE_UUID)
                if (hrService != null) {
                    enableNotification(gatt, hrService, HEART_RATE_MEASUREMENT_CHAR_UUID)
                }

                val radarService = gatt?.getService(RADAR_SERVICE_UUID)
                if (radarService != null) {
                    enableNotification(gatt, radarService, RADAR_DATA_CHAR_UUID)
                    broadcastUpdate("radar_status", "active")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                HEART_RATE_MEASUREMENT_CHAR_UUID -> {
                    // The 'value' parameter contains the raw data from the sensor.
                    if (value.isNotEmpty()) {
                        // The heart rate value is at index 1 and is a UINT8 (unsigned 8-bit integer).
                        // We convert the byte to an Int and use a bitwise AND to handle potential sign issues,
                        // ensuring we get a positive value from 0-255.
                        val heartRate = value[1].toInt() and 0xFF

                        val intent = Intent("heart_rate_update")
                        intent.setPackage(packageName)
                        intent.putExtra("heart_rate", heartRate)
                        sendBroadcast(intent)
                    }
                }

                RADAR_DATA_CHAR_UUID -> {
                    // The 'value' parameter is the same as the old characteristic.value
                    if (value.isNotEmpty()) {
                        val distance = (value[1].toInt() and 0xFF)
                        val intent = Intent("radar_update")
                        intent.setPackage(packageName)
                        intent.putExtra("distance", distance)
                        sendBroadcast(intent)
                    }
                }
            }
        }
    }



    private fun enableNotification(gatt: BluetoothGatt, service: BluetoothGattService, charUuid: UUID) {
        val characteristic = service.getCharacteristic(charUuid) ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        // The check for descriptor != null is important
        if (descriptor != null) {
            // For Android 13+ (API 33) and above, this is the recommended way
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                // For older versions, the deprecated method is still required
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun broadcastUpdate(action: String, status: String) {
        val intent = Intent(action)
        intent.setPackage(packageName)
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        hrGatt?.close()
        radarGatt?.close()
        hrGatt = null
        radarGatt = null
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }
}