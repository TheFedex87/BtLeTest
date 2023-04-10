package it.thefedex87.btletest.bluetooth.presentation

data class BluetoothDeviceUiModel(
    val address: String,
    val name: String? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val batteryLevel: Int? = null
)