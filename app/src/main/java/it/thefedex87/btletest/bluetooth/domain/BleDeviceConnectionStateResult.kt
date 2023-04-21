package it.thefedex87.btletest.bluetooth.domain

import android.bluetooth.BluetoothGatt

sealed interface BleDeviceConnectionStateResult {
    data class DeviceConnected(val device: android.bluetooth.BluetoothDevice): BleDeviceConnectionStateResult
    data class DeviceDisconnected(val device: android.bluetooth.BluetoothDevice): BleDeviceConnectionStateResult
    data class GattConnected(val gatt: BluetoothGatt): BleDeviceConnectionStateResult
    data class ServicesDiscovered(val success: Boolean): BleDeviceConnectionStateResult

    object Close : BleDeviceConnectionStateResult
}