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

    private val _state = MutableStateFlow(
        DevicesUiState(
            devices = listOf(
                BluetoothDeviceUiModel(
                    address = "80:1F:12:B7:90:8C"
                )
            )
        )
    )
    val state = combine(
        _state.asStateFlow(),
        bluetoothController.isScanning
    ) { state, isScanning ->
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
        bluetoothController.devicesState.onEach {
            Log.d("BLE_TEST", "ViewModel has received new deviceState: $it")
            when (it) {
                is BleConnectionState.Connected -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                isConnecting = false,
                                isConnected = true,
                                name = it.name
                            )
                    updateDeviceInList(deviceToUpdate)

                    if (_state.value.devices.all { it.isConnected }) {
                        _state.update {
                            it.copy(
                                connectionState = ConnectionState.CONNECTED
                            )
                        }
                    }

                    val res = bluetoothController.writeCharacteristic(
                        address = it.address,
                        serviceId = "00003ab2-0000-1000-8000-00805f9b34fb",
                        characteristicId = "00001001-0000-1000-8000-00805f9b34fb",
                        value = "abaad486d1884ac8"
                    )
                    Log.d("BLE_TEST", "Res of write is: $res")

                    bluetoothController.registerToCharacteristic(
                        address = it.address,
                        serviceId = "0000180f-0000-1000-8000-00805f9b34fb",
                        characteristicId = "00002a19-0000-1000-8000-00805f9b34fb",
                        descriptorId = "00002902-0000-1000-8000-00805f9b34fb"
                    ).onEach { batteryLevel ->
                        updateDeviceInList(
                            _state.value.devices.first { device -> device.address == it.address }
                                .copy(
                                    batteryLevel = batteryLevel.toInt(radix = 16)
                                )
                        )
                    }.launchIn(viewModelScope)
                }
                is BleConnectionState.Disconnected -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                isConnecting = false,
                                isConnected = false
                            )
                    updateDeviceInList(deviceToUpdate)
                }
                is BleConnectionState.Connecting -> {
                    val deviceToUpdate =
                        _state.value.devices.first { device -> device.address == it.address }
                            .copy(
                                isConnecting = true,
                                isConnected = false
                            )
                    updateDeviceInList(deviceToUpdate)
                }
                is BleConnectionState.Nothing -> {
                    // Nothing
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), BleConnectionState.Nothing)

        bluetoothController.boundDevices.onEach {
            Log.d("BLE_TEST", "Received list of bound devices: $it")
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(), emptyList()
        )
    }

    override fun onCleared() {
        bluetoothController.cleanup()
        super.onCleared()
    }

    fun connect() {
        _state.update {
            it.copy(
                connectionState = ConnectionState.REQUESTED,
            )
        }
        viewModelScope.launch {
            bluetoothController.connectDevices(
                _state.value.devices.map { it.address }
            )
        }
    }

    fun disconnect() {
        bluetoothController.cleanup()
        _state.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
            )
        }
    }

    fun Long.toHexString(): String = java.lang.Long.toHexString(this)
    fun writeCharacteristic() {
        viewModelScope.launch {
            val payload = "1_64396E08"

            val res = bluetoothController.writeCharacteristic(
                "80:1F:12:B7:90:8C",
                "00003ab3-0000-1000-8000-00805f9b34fb",
                "00002001-0000-1000-8000-00805f9b34fb",
                payload
            )
            Log.d("BLE_TEST", "Value of characteristic is: $res")
        }
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