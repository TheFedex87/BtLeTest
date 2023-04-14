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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class AndroidBluetoothController(
    private val context: Context
) : BluetoothController {
    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val controllerJob = SupervisorJob()
    private val controllerScope = CoroutineScope(controllerJob)

    private val devices = MutableStateFlow<List<BluetoothDevice>>(listOf())
    //override val devices: Flow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning = _isScanning.asStateFlow()

    private val _bleStateResult = MutableSharedFlow<BleStateResult>()
    override val bleStateResult: Flow<BleStateResult>
        get() = _bleStateResult.asSharedFlow()

    private val mutex = Mutex(false)

    @SuppressLint("MissingPermission")
    override suspend fun writeCharacteristic2(
        address: String,
        serviceId: String,
        characteristicId: String,
        value: String
    ): BleStateResult {
        val gatt = devices.value.firstOrNull {
            it.address == address
        }?.gatt
        val characteristic = gatt?.services?.firstOrNull {
            it.uuid.toString() == serviceId
        }?.characteristics?.firstOrNull {
            it.uuid.toString() == characteristicId
        }

        Log.d(
            "BLE_TEST",
            "Characteristic $characteristicId is writeable: ${characteristic?.isWritable() ?: "NULL"}"
        )

        val res = characteristic?.let {
            mutex.withLock {
                _bleStateResult.asSharedFlow()
                    .onSubscription {
                        gatt.apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (gatt.writeCharacteristic(
                                        it,
                                        value.toByteArray(Charsets.US_ASCII),
                                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                    ) != BluetoothStatusCodes.SUCCESS) {
                                    emit(BleStateResult.CharacteristicWrote(address, characteristicId, false))
                                }
                            } else {
                                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                it.value = value.toByteArray(Charsets.US_ASCII)
                                if (!gatt.writeCharacteristic(it)) {
                                    emit(BleStateResult.CharacteristicWrote(address, characteristicId, false))
                                }
                            }
                        }
                    }
                    .firstOrNull {
                        it is BleStateResult.CharacteristicWrote
                    } as BleStateResult.CharacteristicWrote? ?: BleStateResult.CharacteristicWrote(address, characteristicId, false)
            }
        } ?: BleStateResult.CharacteristicWrote(address, characteristicId, false)
        return res
    }


    @SuppressLint("MissingPermission")
    override suspend fun connectDevices(addresses: List<String>) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            throw Exception("Missing BLUETOOTH_SCAN permission")
        }

        devices.update {
            addresses.map {
                _bleStateResult.emit(BleStateResult.Connecting(it))

                BluetoothDevice(
                    address = it,
                    gatt = null,
                    /*deviceState = DeviceState(
                        isConnecting = true
                    )*/
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
        val device = devices.value.first { it.address == address }
        device.gatt?.services?.first { service ->
            service.uuid?.toString() == serviceId
        }?.characteristics?.first { characteristic ->
            characteristic.uuid?.toString() == characteristicId
        }?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device.gatt.writeCharacteristic(
                    this,
                    value.toByteArray(Charsets.US_ASCII),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                this.value = value.toByteArray(Charsets.US_ASCII)
                device.gatt.writeCharacteristic(
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
        val device = devices.value.first { it.address == address }
        device.gatt?.services?.first { service ->
            service.uuid?.toString() == serviceId
        }?.getCharacteristic(UUID.fromString(characteristicId))?.apply {
            device.gatt.setCharacteristicNotification(this, true)
            this.getDescriptor(UUID.fromString(descriptorId))?.let { descriptor ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device.gatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    device.gatt.writeDescriptor(
                        descriptor
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun cleanup() {
        devices.value.forEach { device ->
            device.gatt?.disconnect()
            device.gatt?.close()
        }
        devices.update { emptyList() }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            devices.value.find { it.address == result?.device?.address && !it.connectionRequested }
                ?.let { device ->
                    val deviceIndex = devices.value.indexOf(device)
                    val updatedDevice = device.copy(
                        /*deviceState = device.deviceState.copy(
                            connectionRequested = true
                        )*/
                        connectionRequested = true
                    )

                    devices.update { devices ->
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

            if (status == GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                controllerScope.launch {
                    _bleStateResult.emit(
                        BleStateResult.DisconnectionDone(
                            gatt!!.device.address
                        )
                    )
                }
            } else {
                devices.value.find { it.address == gatt?.device?.address }?.let { device ->
                    val deviceIndex = devices.value.indexOf(device)
                    val updatedDevice = device.copy(
                        gatt = gatt
                    )

                    if (status == GATT_SUCCESS) {

                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            /*updatedDevice.deviceState = updatedDevice.deviceState.copy(
                                isConnecting = false,
                                isConnected = true,
                                name = gatt?.device?.name
                            )*/
                            /*controllerScope.launch {
                                _bleStateResult.emit(
                                    BleStateResult.ConnectionEstablished(
                                        gatt!!.device.address,
                                        gatt.device.name
                                    )
                                )
                            }*/

                            gatt?.discoverServices()
                        }
                    } else {
                        controllerScope.launch {
                            _bleStateResult.emit(
                                BleStateResult.ConnectionError(
                                    gatt!!.device.address,
                                    "Error connecting"
                                )
                            )
                        }
                        /*device.deviceState = device.deviceState.copy(
                            isConnected = false,
                            isConnecting = false
                        )*/

                        if (status == 133) {
                            device.gatt?.disconnect()
                            device.gatt?.close()
                        }
                    }

                    devices.update { devices ->
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
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            /*_devices.value.find { it.address == gatt?.device?.address }?.let { device ->
                device.bluetoothComponents = device.bluetoothComponents.copy(
                    services = gatt?.services
                )
            }*/
            /*controllerScope.launch {
                _bleStateResult.emit(BleStateResult.ServicesDiscovered(gatt!!.device!!.address))
            }*/
            controllerScope.launch {
                _bleStateResult.emit(
                    BleStateResult.ConnectionEstablished(
                        gatt!!.device.address,
                        gatt.device.name
                    )
                )
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
                        characteristic!!.uuid!!.toString(),
                        status == GATT_SUCCESS
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

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            _bleStateResult.tryEmit(
                BleStateResult.CharacteristicRead(
                    gatt.device!!.address,
                    characteristic.service.uuid.toString(),
                    value.toString(Charsets.UTF_8)
                )
            )
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            _bleStateResult.tryEmit(
                BleStateResult.CharacteristicRead(
                    gatt!!.device!!.address,
                    characteristic!!.service.uuid.toString(),
                    characteristic.value.toString(Charsets.UTF_8)
                )
            )
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
    return properties and property != 0
}