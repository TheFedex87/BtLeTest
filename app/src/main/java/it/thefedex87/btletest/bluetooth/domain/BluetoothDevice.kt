package it.thefedex87.btletest.bluetooth.domain

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService

typealias BluetoothDeviceDomain = BluetoothDevice

data class BluetoothDevice(
    val address: String,
    val device: android.bluetooth.BluetoothDevice? = null,
    var gatt: BluetoothGatt?,
    val connectionRequested: Boolean = false,
)
