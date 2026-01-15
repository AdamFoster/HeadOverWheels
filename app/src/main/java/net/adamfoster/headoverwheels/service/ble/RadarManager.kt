package net.adamfoster.headoverwheels.service.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import net.adamfoster.headoverwheels.data.RideRepository
import java.util.UUID

class RadarManager(private val repository: RideRepository) : BleSensorManager {

    companion object {
        val RADAR_SERVICE_UUID: UUID = UUID.fromString("6AFF7000-56C8-4203-9068-185C32196F33")
        val RADAR_DATA_CHAR_UUID: UUID = UUID.fromString("6AFF7101-56C8-4203-9068-185C32196F33")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var gatt: BluetoothGatt? = null

    override fun getServiceUuid(): UUID = RADAR_SERVICE_UUID

    override fun onDeviceFound(device: BluetoothDevice, context: Context, callback: (BluetoothGatt?) -> Unit) {
         // Logic handled by service to initiate connection
    }

    fun isConnected(): Boolean = gatt != null

    fun setGatt(gatt: BluetoothGatt) {
        this.gatt = gatt
    }

    override fun onConnected(gatt: BluetoothGatt) {
        this.gatt = gatt
        repository.updateRadarSensorStatus("connected", true)
    }

    override fun onDisconnected() {
        gatt = null
        repository.updateRadarSensorStatus("disconnected", false)
        repository.updateRadarDistance(-1)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt) {
        val service = gatt.getService(RADAR_SERVICE_UUID)
        if (service != null) {
            val characteristic = service.getCharacteristic(RADAR_DATA_CHAR_UUID)
            if (characteristic != null) {
                enableNotification(gatt, characteristic)
                repository.updateRadarSensorStatus("active", true)
            }
        }
    }

    override fun onCharacteristicChanged(uuid: UUID, value: ByteArray) {
        if (uuid == RADAR_DATA_CHAR_UUID && value.isNotEmpty()) {
            val distance = (value[1].toInt() and 0xFF)
            repository.updateRadarDistance(distance)
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
