package net.adamfoster.headoverwheels.service.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import android.util.Log
import net.adamfoster.headoverwheels.data.RideRepository
import java.util.UUID

class HeartRateManager(private val repository: RideRepository) : BleSensorManager {

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var gatt: BluetoothGatt? = null

    override fun getServiceUuid(): UUID = HEART_RATE_SERVICE_UUID

    override fun onDeviceFound(device: BluetoothDevice, context: Context, callback: (BluetoothGatt?) -> Unit) {
        if (gatt == null) {
            Log.i("HeartRateManager", "Connecting to HR Sensor: ${device.address}")
            // Note: The actual connection call is done by the Service using the shared callback
            // This is slightly tricky as we need to pass the specific gatt back to the service if we were initiating here
            // But the service initiates the connection. 
            // We will let the service handle the `connectGatt` and pass the resulting gatt to `onConnected`
            // Wait, the service's `onScanResult` calls `device.connectGatt`.
        }
    }
    
    // Helper to check if we are already connected
    fun isConnected(): Boolean = gatt != null
    
    // We need to set the gatt reference
    fun setGatt(gatt: BluetoothGatt) {
        this.gatt = gatt
    }

    override fun onConnected(gatt: BluetoothGatt) {
        this.gatt = gatt
        repository.updateHrSensorStatus("connected")
    }

    override fun onDisconnected() {
        gatt = null
        repository.updateHeartRate(0)
        repository.updateHrSensorStatus("disconnected")
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt) {
        val service = gatt.getService(HEART_RATE_SERVICE_UUID)
        if (service != null) {
            val characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
            if (characteristic != null) {
                enableNotification(gatt, characteristic)
            }
        }
    }

    override fun onCharacteristicChanged(uuid: UUID, value: ByteArray) {
        if (uuid == HEART_RATE_MEASUREMENT_CHAR_UUID && value.isNotEmpty()) {
            val flags = value[0].toInt()
            val isHeartRateInUInt16 = (flags and 1) != 0
            val heartRate = if (isHeartRateInUInt16) {
                // UInt16: 2nd and 3rd bytes (Little Endian)
                ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
            } else {
                // UInt8: 2nd byte
                value[1].toInt() and 0xFF
            }
            repository.updateHeartRate(heartRate)
        }
    }

    override fun close() {
        gatt?.close()
        gatt = null
    }

    override fun getGatt(): BluetoothGatt? = gatt

    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }
}
