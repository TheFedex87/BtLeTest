package it.thefedex87.btletest.bluetooth.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.thefedex87.btletest.bluetooth.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val bluetoothController: BluetoothController
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState(
        devices = listOf(
            BluetoothDeviceUiModel(
                address = "80:1F:12:B7:90:8C"
            )
        )
    ))
    val state = combine(
        _state.asStateFlow(),
        bluetoothController.selectedDevice,
        bluetoothController.isScanning
    ) { state, selectedDevice, isScanning ->
        state.copy(
            isLoading = isScanning,
            /*devices = devices.map { device ->
                BluetoothDeviceUiModel(
                    address = device.address,
                    name = device.deviceState.name,
                    isConnecting = device.deviceState.isConnecting,
                    isConnected = device.deviceState.isConnected,
                    batteryLevel = state.devices.firstOrNull { it.address == device.address }?.batteryLevel
                )
            }*/
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)


    init {

        bluetoothController.bleStateResult.onEach {
            when (it) {
                is BleStateResult.Connecting -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                isConnecting = true
                            )
                    updateDeviceInList(deviceToUpdate)
                }
                is BleStateResult.ConnectionError -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                isConnecting = false,
                            )
                    updateDeviceInList(deviceToUpdate)
                }
                is BleStateResult.ConnectionEstablished -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                isConnecting = false,
                                isConnected = true,
                                name = it.name
                            )
                    updateDeviceInList(deviceToUpdate)
                }
                is BleStateResult.DisconnectionDone -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                isConnecting = false,
                                isConnected = false
                            )
                    updateDeviceInList(deviceToUpdate)
                }
                is BleStateResult.ServicesDiscovered -> {
                    Log.d("BLE_TEST", "Services discovered for device: ${it.address}")
                    bluetoothController.writeCharacteristic(
                        address = it.address,
                        serviceId = "00003ab2-0000-1000-8000-00805f9b34fb",
                        characteristicId = "00001001-0000-1000-8000-00805f9b34fb",
                        value = "abaad486d1884ac8"
                    )
                }
                is BleStateResult.CharacteristicNotified -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                batteryLevel = it.value.toInt(radix = 16)
                            )
                    _state.update { state ->
                        state.copy(
                            devices = _state.value.devices.map { d ->
                                if (d.address == deviceToUpdate.address) {
                                    deviceToUpdate
                                } else {
                                    d
                                }
                            }
                        )
                    }
                }
                is BleStateResult.CharacteristicWrote -> {
                    if (it.characteristic == "00001001-0000-1000-8000-00805f9b34fb") {
                        bluetoothController.registerToCharacteristic(
                            address = it.address,
                            serviceId = "0000180f-0000-1000-8000-00805f9b34fb",
                            characteristicId = "00002a19-0000-1000-8000-00805f9b34fb",
                            descriptorId = "00002902-0000-1000-8000-00805f9b34fb"
                        )
                    }
                }
            }

        }.launchIn(viewModelScope)

        viewModelScope.launch {
            bluetoothController.connectDevices(
                _state.value.devices.map { it.address }
            )
        }
    }

    override fun onCleared() {
        bluetoothController.cleanup()
        super.onCleared()
    }

    private fun updateDeviceInList(deviceToUpdate: BluetoothDeviceUiModel) {
        _state.update { state ->
            state.copy(
                devices = _state.value.devices.map { d ->
                    if (d.address == deviceToUpdate.address) {
                        deviceToUpdate
                    } else {
                        d
                    }
                }
            )
        }
    }
}