package net.adamfoster.headoverwheels.service.ble

import android.bluetooth.BluetoothGatt
import java.util.UUID

interface BleSensorManager {
    fun getServiceUuid(): UUID
    fun onConnected(gatt: BluetoothGatt)
    fun onDisconnected()
    fun onServicesDiscovered(gatt: BluetoothGatt)
    fun onCharacteristicChanged(uuid: UUID, value: ByteArray)
    fun close()
    fun getGatt(): BluetoothGatt?
}
