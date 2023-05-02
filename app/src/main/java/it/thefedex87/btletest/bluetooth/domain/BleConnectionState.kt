package it.thefedex87.btletest.bluetooth.domain

sealed interface BleConnectionState {
    data class Disconnected(val address: String) : BleConnectionState
    data class Connecting(val address: String) : BleConnectionState
    data class Connected(val address: String, val name: String) : BleConnectionState
    object Nothing: BleConnectionState
}