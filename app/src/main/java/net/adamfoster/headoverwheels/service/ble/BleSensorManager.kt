package net.adamfoster.headoverwheels.service.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import java.util.UUID

interface BleSensorManager {
    fun getServiceUuid(): UUID
    fun onDeviceFound(device: BluetoothDevice, context: Context, callback: (BluetoothGatt?) -> Unit)
    fun onConnected(gatt: BluetoothGatt)
    fun onDisconnected()
    fun onServicesDiscovered(gatt: BluetoothGatt)
    fun onCharacteristicChanged(uuid: UUID, value: ByteArray)
    fun close()
    fun getGatt(): BluetoothGatt?
}
