package it.thefedex87.btletest.bluetooth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.thefedex87.btletest.bluetooth.domain.BluetoothController
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
        state.copy(
            isLoading = isScanning,
            selectedDeviceInfo = selectedDevice?.deviceState,
            devices = devices.map { it.deviceState }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    init {

    }
}