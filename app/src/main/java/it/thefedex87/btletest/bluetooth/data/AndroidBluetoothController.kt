package it.thefedex87.btletest.bluetooth.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import it.thefedex87.btletest.bluetooth.domain.BluetoothComponents
import it.thefedex87.btletest.bluetooth.domain.BluetoothController
import it.thefedex87.btletest.bluetooth.domain.BluetoothDevice
import it.thefedex87.btletest.bluetooth.domain.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AndroidBluetoothController(
    private val context: Context
) : BluetoothController {
    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val controllerJob = SupervisorJob()
    private val controllerScope = CoroutineScope(controllerJob)

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val devices: Flow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning = _isScanning.asStateFlow()

    private val _selectedDeviceState = MutableStateFlow<BluetoothDevice?>(null)
    override val selectedDevice: Flow<BluetoothDevice?>
        get() = _selectedDeviceState.asStateFlow()

    override fun changeSelectedDevice(address: String) {
        _selectedDeviceState.update {
            _devices.value.find { it.address == address }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectDevices(addresses: List<String>) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            throw Exception("Missing BLUETOOTH_SCAN permission")
        }

        _devices.update {
            addresses.map {
                BluetoothDevice(
                    address = it,
                    bluetoothComponents = BluetoothComponents(
                        gatt = null,
                        services = null
                    ),
                    deviceState = DeviceState(
                        isConnecting = true
                    )
                )
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _isScanning.update { true }
        bluetoothAdapter?.bluetoothLeScanner?.startScan(
            listOf(),
            settings,
            scanCallback
        )
        controllerScope.launch {
            delay(7000)
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.update { false }
        }
    }

    @SuppressLint("MissingPermission")
    override fun cleanup() {
        _devices.value.forEach { device ->
            device.bluetoothComponents.gatt?.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            if(_devices.value.any { it.address == result?.device?.address }) {
                result?.device?.connectGatt(
                    context,
                    true,
                    gattCallback
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)

            _devices.value.find { it.address == gatt?.device?.address }?.let { device ->
                if (status == GATT_SUCCESS) {
                    device.bluetoothComponents =
                        device.bluetoothComponents.copy(
                            gatt = gatt
                        )
                    device.deviceState = device.deviceState.copy(
                        name = gatt?.device?.name
                    )

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        device.deviceState = device.deviceState.copy(
                            isConnecting = false,
                            isConnected = true
                        )

                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        device.deviceState = device.deviceState.copy(
                            isConnecting = false,
                            isConnected = false
                        )
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            _devices.value.find { it.address == gatt?.device?.address }?.let { device ->
                device.bluetoothComponents = device.bluetoothComponents.copy(
                    services = gatt?.services
                )
            }
            super.onServicesDiscovered(gatt, status)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}