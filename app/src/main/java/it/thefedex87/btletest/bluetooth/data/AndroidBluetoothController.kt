package it.thefedex87.btletest.bluetooth.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import it.thefedex87.btletest.bluetooth.domain.*
import it.thefedex87.btletest.bluetooth.domain.BluetoothDevice
import it.thefedex87.btletest.utils.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

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

    private val _bleStateResult = MutableSharedFlow<BleStateResult>()
    override val bleStateResult: Flow<BleStateResult>
        get() = _bleStateResult.asSharedFlow()

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
    override fun writeCharacteristic(
        address: String,
        serviceId: String,
        characteristicId: String,
        value: String
    ) {
        val service = _devices.value.first { it.address == address }
        service.bluetoothComponents.services?.first { service ->
            service.uuid?.toString() == serviceId
        }?.characteristics?.first { characteristic ->
            characteristic.uuid?.toString() == characteristicId
        }?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                service.bluetoothComponents.gatt?.writeCharacteristic(
                    this,
                    value.toByteArray(Charsets.US_ASCII),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                this.value = value.toByteArray(Charsets.US_ASCII)
                service.bluetoothComponents.gatt?.writeCharacteristic(
                    this
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun registerToCharacteristic(
        address: String,
        serviceId: String,
        characteristicId: String,
        descriptorId: String
    ) {
        val device = _devices.value.first { it.address == address }
        device.bluetoothComponents.services?.first { service ->
            service.uuid?.toString() == serviceId
        }?.getCharacteristic(UUID.fromString(characteristicId))?.apply {
            device.bluetoothComponents.gatt?.setCharacteristicNotification(this, true)
            this.getDescriptor(UUID.fromString(descriptorId))?.let { descriptor ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device.bluetoothComponents.gatt?.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    device.bluetoothComponents.gatt?.writeDescriptor(
                        descriptor
                    )
                }
            }
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

            _devices.value.find { it.address == result?.device?.address && !it.deviceState.connectionRequested }
                ?.let { device ->
                    val deviceIndex = _devices.value.indexOf(device)
                    val updatedDevice = device.copy(
                        deviceState = device.deviceState.copy(
                            connectionRequested = true
                        )
                    )

                    _devices.update { devices ->
                        devices.map { device ->
                            if (devices.indexOf(device) == deviceIndex) {
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
                _devices.update { devices ->
                    devices.map { device ->
                        if (devices.indexOf(device) == deviceIndex) {
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
            controllerScope.launch {
                _bleStateResult.emit(BleStateResult.ServicesDiscovered(gatt!!.device!!.address))
            }
            super.onServicesDiscovered(gatt, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            controllerScope.launch {
                _bleStateResult.emit(
                    BleStateResult.CharacteristicWrote(
                        gatt!!.device!!.address,
                        characteristic!!.uuid!!.toString()
                    )
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            controllerScope.launch {
                _bleStateResult.emit(
                    BleStateResult.CharacteristicNotified(
                        address = gatt!!.device!!.address,
                        serviceId = characteristic!!.service.uuid.toString(),
                        characteristicId = characteristic.uuid.toString(),
                        value = characteristic.value.toHexString().replace("0x", "")
                    )
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            controllerScope.launch {
                _bleStateResult.emit(
                    BleStateResult.CharacteristicNotified(
                        address = gatt.device!!.address,
                        serviceId = characteristic.service.uuid.toString(),
                        characteristicId = characteristic.uuid.toString(),
                        value = value.toString(Charsets.UTF_8)
                    )
                )
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}