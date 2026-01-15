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
import net.adamfoster.headoverwheels.data.RideRepository
import net.adamfoster.headoverwheels.service.ble.BleSensorManager
import net.adamfoster.headoverwheels.service.ble.HeartRateManager
import net.adamfoster.headoverwheels.service.ble.RadarManager
import java.util.*

@SuppressLint("MissingPermission")
class BleService : Service() {

    companion object {
        const val CHANNEL_ID = "BleServiceChannel"
        const val NOTIFICATION_ID = 2
        const val ACTION_START_SCAN = "net.adamfoster.headoverwheels.action.START_SCAN"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    
    private lateinit var hrManager: HeartRateManager
    private lateinit var radarManager: RadarManager

    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Ready to connect sensors"))

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        // Initialize managers with Repository
        hrManager = HeartRateManager(RideRepository)
        radarManager = RadarManager(RideRepository)
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
             startScanning()
        }
        return START_STICKY
    }

    private fun startScanning() {
        if (isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

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

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val uuids = result.scanRecord?.serviceUuids ?: return

            // Check for Heart Rate
            if (uuids.contains(ParcelUuid(HeartRateManager.HEART_RATE_SERVICE_UUID))) {
                if (!hrManager.isConnected()) {
                    Log.i("BleService", "Found HR Sensor: ${device.address}")
                    val gatt = device.connectGatt(this@BleService, true, gattCallback)
                    hrManager.setGatt(gatt)
                    updateNotification("Connecting HR Sensor...")
                }
            }
            
            // Check for Radar
            if (uuids.contains(ParcelUuid(RadarManager.RADAR_SERVICE_UUID))) {
                 if (!radarManager.isConnected()) {
                    Log.i("BleService", "Found Radar: ${device.address}")
                    val gatt = device.connectGatt(this@BleService, true, gattCallback)
                    radarManager.setGatt(gatt)
                    updateNotification("Connecting Radar...")
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BleService", "Connected to GATT server: ${gatt.device.address}")
                gatt.discoverServices()
                
                // We need to know which manager this gatt belongs to.
                // Since we have separate managers but share a callback in this implementation, 
                // we can check the service list later or match device address if we stored it.
                // However, the managers store their gatt reference.
                
                // Simple check:
                // This logic assumes 1 device per type.
                
                // We'll dispatch to both, they can check if it's their gatt (by reference equality) 
                // BUT we passed 'gatt' to 'setGatt' before connection completed in scanCallback.
                // So the equality check works.
                
                if (hrManager.isConnected() && isGattForManager(gatt, hrManager)) {
                     hrManager.onConnected(gatt)
                     updateNotification("HR Connected")
                }
                if (radarManager.isConnected() && isGattForManager(gatt, radarManager)) {
                     radarManager.onConnected(gatt)
                     updateNotification("Radar Connected")
                }
                
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BleService", "Disconnected from GATT server.")
                
                 if (hrManager.isConnected() && isGattForManager(gatt, hrManager)) {
                     hrManager.onDisconnected()
                     updateNotification("HR Disconnected")
                }
                if (radarManager.isConnected() && isGattForManager(gatt, radarManager)) {
                     radarManager.onDisconnected()
                     updateNotification("Radar Disconnected")
                }
                
                gatt.close()
                startScanning()
            }
        }
        
        // Reflection or additional field would be cleaner, but for now:
        // We can't easily check "isGattForManager" without exposing the internal gatt from manager.
        // Let's rely on the fact that we only have two.
        
        private fun isGattForManager(gatt: BluetoothGatt, manager: BleSensorManager): Boolean {
             return manager.getGatt() == gatt
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                hrManager.onServicesDiscovered(gatt)
                radarManager.onServicesDiscovered(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
             hrManager.onCharacteristicChanged(characteristic.uuid, value)
             radarManager.onCharacteristicChanged(characteristic.uuid, value)
        }
    }
    
    // Quick fix helper for the "isGattForManager" issue:
    // We don't strictly need to filter in onConnected because discoverServices will sort it out,
    // and onDisconnected will clean up.
    // However, knowing which one connected allows for better notifications.
    // I'll leave the notification logic generic or check ServicesDiscovered for precise notifications.

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        hrManager.close()
        radarManager.close()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }
}
