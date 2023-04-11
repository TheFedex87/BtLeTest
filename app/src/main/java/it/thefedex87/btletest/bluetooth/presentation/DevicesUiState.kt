package it.thefedex87.btletest.bluetooth.presentation

data class DevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<BluetoothDeviceUiModel> = emptyList(),
    val selectedDeviceInfo: BluetoothDeviceUiModel? = null
)
