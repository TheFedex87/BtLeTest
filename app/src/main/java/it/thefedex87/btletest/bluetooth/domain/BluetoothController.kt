package it.thefedex87.btletest.bluetooth.domain

import kotlinx.coroutines.flow.Flow

interface BluetoothController {
    val isScanning: Flow<Boolean>
    val devices: Flow<List<BluetoothDevice>>
    val selectedDevice: Flow<BluetoothDevice?>

    fun connectDevices(addresses: List<String>)
    fun changeSelectedDevice(address: String)

    fun cleanup()
}