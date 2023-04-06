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
import android.util.Log
import it.thefedex87.btletest.bluetooth.domain.BluetoothComponents
import it.thefedex87.btletest.bluetooth.domain.BluetoothController
import it.thefedex87.btletest.bluetooth.domain.BluetoothDevice
import it.thefedex87.btletest.bluetooth.domain.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AndroidBluetoothController(
    private val context: Context
) : BluetoothController {
    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val controllerJob = SupervisorJob()
    private val controllerScope = CoroutineScope(controllerJob)

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(listOf())
    override val devices: Flow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning = _isScanning.asStateFlow()

    private val _error = MutableSharedFlow<String?>()
    override val error: Flow<String?>
        get() = _error.asSharedFlow()

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
            device.bluetoothComponents.gatt?.close()
        }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            _devices.value.find { it.address == result?.device?.address && !it.deviceState.connectionRequested }?.let { device ->
                val deviceIndex = _devices.value.indexOf(device)
                val updatedDevice = device.copy(
                    deviceState = device.deviceState.copy(
                        connectionRequested = true
                    )
                )

                _devices.update {devices ->
                    devices.map { device ->
                        if(devices.indexOf(device) == deviceIndex) {
                            updatedDevice
                        } else {
                            device
                        }
                    }
                }

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
                val deviceIndex = _devices.value.indexOf(device)
                val updatedDevice = device.copy()

                if (status == GATT_SUCCESS) {
                    updatedDevice.bluetoothComponents =
                        updatedDevice.bluetoothComponents.copy(
                            gatt = gatt
                        )

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        updatedDevice.deviceState = updatedDevice.deviceState.copy(
                            isConnecting = false,
                            isConnected = true,
                            name = gatt?.device?.name
                        )

                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        updatedDevice.deviceState = updatedDevice.deviceState.copy(
                            isConnecting = false,
                            isConnected = false,
                            name = gatt?.device?.name
                        )
                    }
                } else {
                    controllerScope.launch {
                        _error.emit("Error connecting")
                    }
                    device.deviceState = device.deviceState.copy(
                        isConnected = false,
                        isConnecting = false
                    )

                    if (status == 133) {
                        device.bluetoothComponents.gatt?.disconnect()
                        device.bluetoothComponents.gatt?.close()
                    }
                }

                Log.d("BLE_TEST", "DS: ${device.deviceState}")
                _devices.update {devices ->
                    devices.map { device ->
                        if(devices.indexOf(device) == deviceIndex) {
                            updatedDevice
                        } else {
                            device
                        }
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