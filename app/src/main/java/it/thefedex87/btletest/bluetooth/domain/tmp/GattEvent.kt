package it.thefedex87.btletest.bluetooth.domain.tmp

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt

sealed class GattEvent(val deviceAddress: String) {
    data class DeviceDiscovered(val device: BluetoothDevice) : GattEvent(device.address)
    data class DeviceConnecting(val device: BluetoothDevice) : GattEvent(device.address)
    data class DeviceConnected(val gatt: BluetoothGatt, val servicesDiscovered: Boolean) : GattEvent(gatt.device.address)
    data class DeviceDisconnected(val gatt: BluetoothGatt) : GattEvent(gatt.device.address)
    data class ServicesDiscovered(val address: String, val success: Boolean) : GattEvent(address)
    data class CharacteristicNotified(
        val address: String,
        val serviceId: String,
        val characteristicId: String,
        val value: String
    ) : GattEvent(address)
    data class CharacteristicWrote(
        val address: String,
        val serviceId: String,
        val characteristicId: String,
        val success: Boolean
    ) : GattEvent(address)
}