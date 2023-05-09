package it.thefedex87.btletest.bluetooth.domain

import it.thefedex87.btletest.bluetooth.domain.tmp.GattEvent
import kotlinx.coroutines.flow.Flow

interface BluetoothController {
    val isScanning: Flow<Boolean>
    //val devices: Flow<List<BluetoothDevice>>
    //val bleStateResult: Flow<BleStateResult>
    val devicesState: Flow<BleConnectionState>

    val boundDevices: Flow<List<String>>

    suspend fun connectDevices(addresses: List<String>)

    suspend fun writeCharacteristic(
        address: String,
        serviceId: String,
        characteristicId: String,
        value: String
    ): GattEvent

    //fun writeCharacteristic(address: String, serviceId: String, characteristicId: String, value: String)
    //fun registerToCharacteristic(address: String, serviceId: String, characteristicId: String, descriptorId: String)
    fun registerToCharacteristic(address: String, serviceId: String, characteristicId: String, descriptorId: String): Flow<String>

    fun cleanup()
}