package it.thefedex87.btletest.bluetooth.domain

import android.bluetooth.BluetoothGatt

sealed interface BleDeviceStateResult {
    data class DeviceConnected(val device: android.bluetooth.BluetoothDevice): BleDeviceStateResult
    data class DeviceConnecting(val device: android.bluetooth.BluetoothDevice): BleDeviceStateResult
    data class DeviceDisconnected(val device: android.bluetooth.BluetoothDevice): BleDeviceStateResult
    data class GattConnected(val gatt: BluetoothGatt): BleDeviceStateResult
    data class ServicesDiscovered(val success: Boolean): BleDeviceStateResult

    data class Error(val address: String, val message: String): BleDeviceStateResult

    data class CharacteristicWrote(val address: String, val characteristic: String, val success: Boolean): BleDeviceStateResult
    data class CharacteristicRead(val address: String, val characteristic: String, val value: String): BleDeviceStateResult
    data class CharacteristicNotified(
        val address: String,
        val serviceId: String, val characteristicId: String,
        val value: String
    ): BleDeviceStateResult
}