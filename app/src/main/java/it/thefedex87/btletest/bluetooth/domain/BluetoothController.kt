package it.thefedex87.btletest.bluetooth.domain

import kotlinx.coroutines.flow.Flow

interface BluetoothController {
    val isScanning: Flow<Boolean>
    val devices: Flow<List<BluetoothDevice>>
    val selectedDevice: Flow<BluetoothDevice?>

    val bleStateResult: Flow<BleStateResult>

    val error: Flow<String?>

    fun connectDevices(addresses: List<String>)
    fun changeSelectedDevice(address: String)
    fun writeCharacteristic(address: String, serviceId: String, characteristicId: String, value: String)
    fun registerToCharacteristic(address: String, serviceId: String, characteristicId: String, descriptorId: String)

    fun cleanup()
}