package it.thefedex87.btletest.bluetooth.domain

import kotlinx.coroutines.flow.Flow

interface BluetoothController {
    val isScanning: Flow<Boolean>
    //val devices: Flow<List<BluetoothDevice>>
    //val bleStateResult: Flow<BleStateResult>
    val devicesState: Flow<BleConnectionState>

    suspend fun connectDevices(addresses: List<String>)

    suspend fun writeCharacteristic(
        address: String,
        serviceId: String,
        characteristicId: String,
        value: String
    ): BleDeviceStateResult

    //fun writeCharacteristic(address: String, serviceId: String, characteristicId: String, value: String)
    //fun registerToCharacteristic(address: String, serviceId: String, characteristicId: String, descriptorId: String)
    fun registerToCharacteristic(address: String, serviceId: String, characteristicId: String, descriptorId: String): Flow<String>

    fun cleanup()
}