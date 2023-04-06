package it.thefedex87.btletest.bluetooth.domain

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService

typealias BluetoothDeviceDomain = BluetoothDevice

data class BluetoothDevice(
    val address: String,
    var bluetoothComponents: BluetoothComponents,
    var deviceState: DeviceState
)

data class BluetoothComponents(
    val gatt: BluetoothGatt?,
    val services: List<BluetoothGattService>?,
)

data class DeviceState(
    val connectionRequested: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,

    val name: String? = null,
    val battery: Int? = null
)
