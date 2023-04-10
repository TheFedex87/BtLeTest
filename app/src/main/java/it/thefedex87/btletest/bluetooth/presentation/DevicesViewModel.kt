package it.thefedex87.btletest.bluetooth.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.thefedex87.btletest.bluetooth.domain.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val bluetoothController: BluetoothController
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state = combine(
        _state.asStateFlow(),
        bluetoothController.selectedDevice,
        bluetoothController.isScanning,
        bluetoothController.devices
    ) { state, selectedDevice, isScanning, devices ->
        _state.update {
            state.copy(
                isLoading = isScanning,
                devices = devices.map { device ->
                    BluetoothDeviceUiModel(
                        address = device.address,
                        name = device.deviceState.name,
                        isConnecting = device.deviceState.isConnecting,
                        isConnected = device.deviceState.isConnected,
                        batteryLevel = _state.value.devices.first { it.address == device.address }.batteryLevel
                    )
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)


    init {

        bluetoothController.bleStateResult.onEach {
            when (it) {
                is BleStateResult.ServicesDiscovered -> {
                    Log.d("BLE_TEST", "Services discovered for device: ${it.address}")
                    bluetoothController.writeCharacteristic(
                        address = it.address,
                        serviceId = "00003ab2-0000-1000-8000-00805f9b34fb",
                        characteristicId = "00001001-0000-1000-8000-00805f9b34fb",
                        value = "access_token"
                    )
                }
                is BleStateResult.CharacteristicNotified -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                batteryLevel = it.value.toInt()
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

        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        bluetoothController.connectDevices(
            listOf(
                "80:1F:12:B7:90:8C"
            )
        )
    }
}