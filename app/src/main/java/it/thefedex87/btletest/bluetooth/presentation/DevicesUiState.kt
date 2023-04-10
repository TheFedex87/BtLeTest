package it.thefedex87.btletest.bluetooth.presentation

import it.thefedex87.btletest.bluetooth.domain.BluetoothDevice
import it.thefedex87.btletest.bluetooth.domain.DeviceState

data class DevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<BluetoothDeviceUiModel> = emptyList(),
    val selectedDeviceInfo: BluetoothDeviceUiModel? = null
)
