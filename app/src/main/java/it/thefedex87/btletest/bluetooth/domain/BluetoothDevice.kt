package it.thefedex87.btletest.bluetooth.domain

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService

typealias BluetoothDeviceDomain = BluetoothDevice

data class BluetoothDevice(
    val address: String,
    val gatt: BluetoothGatt?,
    val connectionRequested: Boolean = false,
)
