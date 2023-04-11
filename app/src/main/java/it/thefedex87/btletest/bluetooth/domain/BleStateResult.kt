package it.thefedex87.btletest.bluetooth.domain

sealed interface BleStateResult {
    //data class DeviceResultConnectionEstablished(val address: String?, val gattServer: BluetoothGattServer?): ConnectionStateResult
    //data class GattResultConnectionEstablished(val address: String?): ConnectionStateResult
    data class Connecting(val address: String): BleStateResult
    data class ConnectionError(val address: String, val errorMessage: String): BleStateResult
    data class ConnectionEstablished(val address: String, val name: String): BleStateResult
    data class DisconnectionDone(val address: String): BleStateResult
    data class ServicesDiscovered(val address: String): BleStateResult
    data class CharacteristicWrote(val address: String, val characteristic: String): BleStateResult
    data class CharacteristicNotified(
        val address: String,
        val serviceId: String, val characteristicId: String,
        val value: String
    ): BleStateResult
}