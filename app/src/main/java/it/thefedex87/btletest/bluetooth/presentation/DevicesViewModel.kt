package it.thefedex87.btletest.bluetooth.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.thefedex87.btletest.bluetooth.domain.BluetoothComponents
import it.thefedex87.btletest.bluetooth.domain.BluetoothController
import it.thefedex87.btletest.bluetooth.domain.BluetoothDevice
import it.thefedex87.btletest.bluetooth.domain.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        Log.d("BLE_TEST", "Raised event: ${devices[0].deviceState}")
        state.copy(
            isLoading = isScanning,
            selectedDeviceInfo = selectedDevice?.deviceState,
            devices = devices.map { it.deviceState }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)


    init {
        bluetoothController.connectDevices(listOf(
            "80:1F:12:B7:90:8C"
        ))
    }
}