package net.adamfoster.headoverwheels.service.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import net.adamfoster.headoverwheels.data.RideRepository
import java.util.UUID

class RadarManager(
    private val repository: RideRepository,
    private val onAlert: () -> Unit = {}
) : BleSensorManager {

    companion object {
        val RADAR_SERVICE_UUID: UUID = UUID.fromString("6A4E3200-667B-11E3-949A-0800200C9A66")
        val RADAR_DATA_CHAR_UUID: UUID = UUID.fromString("6A4E3203-667B-11E3-949A-0800200C9A66")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var gatt: BluetoothGatt? = null
    private var lastDistance = -1

    override fun getServiceUuid(): UUID = RADAR_SERVICE_UUID

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
        if (uuid == RADAR_DATA_CHAR_UUID) {
            // Data format: [Header (1 byte)] + [Threat 1 (3 bytes)] + [Threat 2 (3 bytes)] ...
            // Threat: [ID?, Distance, Speed?]
            // If length is 1, it means no threats are detected.
            
            if (value.size <= 1) {
                repository.updateRadarDistance(-1)
                lastDistance = -1
                return
            }

            var closestDistance = Int.MAX_VALUE
            var hasThreats = false

            // Iterate through threats. Each threat is 3 bytes long.
            // Start at index 1 (skip header).
            for (i in 1 until value.size step 3) {
                if (i + 2 >= value.size) break  // skip incomplete triplet at end of packet
                val dist = (value[i + 1].toInt() and 0xFF)
                if (dist < closestDistance) {
                    closestDistance = dist
                }
                hasThreats = true
            }

            if (hasThreats && closestDistance != Int.MAX_VALUE) {
                repository.updateRadarDistance(closestDistance)
                
                // Trigger alert if we crossed the threshold
                if (closestDistance < 80 && (lastDistance >= 80 || lastDistance == -1)) {
                    onAlert()
                }
                lastDistance = closestDistance
            } else {
                repository.updateRadarDistance(-1)
                lastDistance = -1
            }
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
