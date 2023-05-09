package it.thefedex87.btletest.bluetooth.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED
import android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import it.thefedex87.btletest.bluetooth.domain.*
import it.thefedex87.btletest.bluetooth.domain.BluetoothDevice
import it.thefedex87.btletest.bluetooth.domain.tmp.GattEvent
import it.thefedex87.btletest.utils.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class AndroidBluetoothController2(
    private val context: Context
) : BluetoothController {
    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val controllerJob = SupervisorJob()
    private val controllerScope = CoroutineScope(controllerJob)

    private val devices = MutableStateFlow<List<BluetoothDevice>>(listOf())

    private val gattEvent = MutableSharedFlow<GattEvent>()

    private val mutex = Mutex(false)

    private val _isScanning = MutableStateFlow<Boolean>(false)
    override val isScanning: Flow<Boolean>
        get() = _isScanning.asStateFlow()

    private val _boundDevices = MutableStateFlow<List<android.bluetooth.BluetoothDevice>>(emptyList())
    override val boundDevices: Flow<List<String>>
        get() = _boundDevices.asStateFlow().map {
            it.map { it.address }
        }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("BLE_TEST", "Received action in bondStateReceiver is: ${intent?.action} with extra ${intent?.getIntExtra(EXTRA_BOND_STATE, -99)}")
        }
    }

    init {
        updatePairedDevices()
    }

    @SuppressLint("MissingPermission")
    private fun updatePairedDevices() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        bluetoothAdapter
            ?.bondedDevices
            ?.map { it }
            ?.also { devices ->
                _boundDevices.update { devices }
            }
    }

    override val devicesState: Flow<BleConnectionState>
        @SuppressLint("MissingPermission")
        get() = flow {
            emitAll(
                gattEvent.asSharedFlow().filter {
                    /*devices.value.any { d ->
                        d.address == it.deviceAddress
                    } &&*/((it is GattEvent.DeviceConnected && it.servicesDiscovered) || it is GattEvent.DeviceConnecting || it is GattEvent.DeviceDisconnected)
                }.map {
                    Log.d("BLE_TEST", "devicesState received gattEvent: $it")
                    when (it) {
                        is GattEvent.DeviceConnected -> {
                            BleConnectionState.Connected(
                                it.deviceAddress,
                                it.gatt.device.name
                            )
                        }
                        is GattEvent.DeviceConnecting -> {
                            BleConnectionState.Connecting(
                                it.deviceAddress
                            )
                        }
                        is GattEvent.DeviceDisconnected -> {
                            BleConnectionState.Disconnected(
                                it.deviceAddress
                            )
                        }
                        else -> {
                            //NOTHING
                            BleConnectionState.Nothing
                        }
                    }
                }
            )
        }

    @SuppressLint("MissingPermission")
    override suspend fun connectDevices(addresses: List<String>) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            throw Exception("Missing BLUETOOTH_SCAN permission")
        }

        context.registerReceiver(bondStateReceiver, IntentFilter(ACTION_BOND_STATE_CHANGED))

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        devices.update {
            addresses.map {
                BluetoothDeviceDomain(
                    address = it,
                    device = null,
                    gatt = null
                )
            }
        }

        val connectedList = mutableListOf<BluetoothDevice>()
        addresses.forEach { address ->
            _boundDevices.value
                .firstOrNull { it.address == address }
                ?.let {
                    connectedList.add(
                        BluetoothDeviceDomain(
                            address = it.address,
                            device = it,
                            gatt = null
                        )
                    )
                }
        }

        if(connectedList.size < addresses.size) {
            _isScanning.update { true }
            mutex.withLock {
                val connectCollectorJob = controllerScope.launch {
                    gattEvent.onSubscription {
                        bluetoothAdapter?.bluetoothLeScanner?.startScan(
                            listOf(),
                            settings,
                            scanCallback
                        )
                    }.collect { result ->
                        if (result is GattEvent.DeviceDiscovered) {
                            if (!connectedList.any { it.address == result.device.address } && addresses.contains(
                                    result.device.address
                                )) {
                                //emit(BleConnectionState.Connecting(result.device.address))
                                connectedList.add(
                                    BluetoothDevice(
                                        address = result.device.address,
                                        device = result.device,
                                        gatt = null
                                    )
                                )
                            }
                        }
                    }
                }

                delay(7000)
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
                _isScanning.update { false }
                connectCollectorJob.cancel()
            }
        }

        connectedList.forEach {
            mutex.withLock {
                controllerScope.launch {
                    gattEvent.emit(
                        GattEvent.DeviceConnecting(it.device!!)
                    )
                }

                val res = gattEvent.onSubscription {
                    it.device!!.connectGatt(context, true, gattCallback)
                }.firstOrNull {
                    it is GattEvent.DeviceConnected
                } as GattEvent.DeviceConnected

                it.gatt = res.gatt

                //it.device!!.createBond()
            }
        }


        connectedList.forEach {
            mutex.withLock {
                gattEvent.onSubscription {
                    if (!it.gatt!!.discoverServices()) {
                        emit(GattEvent.ServicesDiscovered(it.address, success = false))
                    }
                }.firstOrNull {
                    it is GattEvent.ServicesDiscovered
                }
            }
        }


        devices.update {
            connectedList.filter { it.gatt?.services != null }
        }

        Log.d("BLE_TEST", "Connection flow end")
        devices.value.forEach {
            gattEvent.emit(
                GattEvent.DeviceConnected(it.gatt!!, true)
            )
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeCharacteristic(
        address: String,
        serviceId: String,
        characteristicId: String,
        value: String
    ): GattEvent {
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
                gattEvent.asSharedFlow()
                    .onSubscription {
                        gatt.apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (gatt.writeCharacteristic(
                                        it,
                                        value.toByteArray(Charsets.US_ASCII),
                                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                    ) != BluetoothStatusCodes.SUCCESS
                                ) {
                                    emit(
                                        GattEvent.CharacteristicWrote(
                                            address,
                                            serviceId,
                                            characteristicId,
                                            false
                                        )
                                    )
                                }
                            } else {
                                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                it.value = value.toByteArray(Charsets.US_ASCII)
                                if (!gatt.writeCharacteristic(it)) {
                                    emit(
                                        GattEvent.CharacteristicWrote(
                                            address,
                                            serviceId,
                                            characteristicId,
                                            false
                                        )
                                    )
                                }
                            }
                        }
                    }
                    .firstOrNull {
                        it is GattEvent.CharacteristicWrote
                    } as GattEvent.CharacteristicWrote? ?: GattEvent.CharacteristicWrote(
                    address,
                    serviceId,
                    characteristicId,
                    false
                )
            }
        } ?: GattEvent.CharacteristicWrote(address, serviceId, characteristicId, false)


        return res
    }

    @SuppressLint("MissingPermission")
    override fun registerToCharacteristic(
        address: String,
        serviceId: String,
        characteristicId: String,
        descriptorId: String
    ): Flow<String> = flow {
        val device = devices.value.first { it.address == address }
        device.gatt?.services?.first { service ->
            service.uuid?.toString() == serviceId
        }?.getCharacteristic(UUID.fromString(characteristicId))?.apply {
            val characteristic = this

            try {
                emitAll(
                    gattEvent.onSubscription {
                        device.gatt!!.setCharacteristicNotification(characteristic, true)
                        characteristic.getDescriptor(UUID.fromString(descriptorId))?.let { descriptor ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                device.gatt!!.writeDescriptor(
                                    descriptor,
                                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                )
                            } else {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                device.gatt!!.writeDescriptor(
                                    descriptor
                                )
                            }
                        }
                    }.filter {
                        it is GattEvent.CharacteristicNotified && it.characteristicId == characteristicId
                    }.map { state ->
                        (state as GattEvent.CharacteristicNotified).value
                    }
                )
            } catch (ex: Exception) {
                Log.d("BLE_TEST", "Stop update notification requested")
            }
        }
    }


    @SuppressLint("MissingPermission")
    override fun cleanup() {
        context.unregisterReceiver(bondStateReceiver)

        controllerScope.launch {
            devices.value.forEach { device ->
                mutex.withLock {
                    device.gatt?.let { gatt ->
                        gattEvent.onSubscription {
                            gatt.disconnect()
                        }.firstOrNull {
                            it is GattEvent.DeviceDisconnected
                        }
                        gatt.close()
                    }
                }
            }

            devices.update { emptyList() }

            Log.d("BLE_TEST", "Cleanup end")
        }
    }

    // DISCOVER CALLBACK
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            result?.device?.let { d ->
                controllerScope.launch {
                    gattEvent.emit(
                        GattEvent.DeviceDiscovered(d)
                    )
                }
            }
        }
    }

    // GATT EVENTS CALLBACK
    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d("BLE_TEST", "Received connection state changed: $status - $newState")
            if (status == GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                controllerScope.launch {
                    Log.d("BLE_TEST", "Emitting disconnect")
                    gattEvent.emit(
                        GattEvent.DeviceDisconnected(gatt!!)
                    )
                }
            } else {
                if (status == GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        controllerScope.launch {
                            gattEvent.emit(
                                GattEvent.DeviceConnected(gatt!!, gatt.services.size > 0)
                            )
                        }
                    }
                } else {
                    /*controllerScope.launch {
                        bleDeviceStateResult.emit(
                            BleDeviceStateResult.Error(
                                gatt!!.device.address,
                                "Error connecting"
                            )
                        )
                    }*/

                    if (status == 133) {
                        gatt?.disconnect()
                        gatt?.close()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            controllerScope.launch {
                gattEvent.emit(
                    GattEvent.ServicesDiscovered(gatt!!.device.address, status == GATT_SUCCESS)
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
                gattEvent.emit(
                    GattEvent.CharacteristicWrote(
                        gatt!!.device.address,
                        characteristic!!.service.uuid.toString(),
                        characteristic.uuid.toString(),
                        true
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
                gattEvent.emit(
                    GattEvent.CharacteristicNotified(
                        gatt!!.device.address,
                        characteristic!!.service.uuid.toString(),
                        characteristic.uuid.toString(),
                        value.toString(Charsets.UTF_8)
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
                gattEvent.emit(
                    GattEvent.CharacteristicNotified(
                        gatt!!.device.address,
                        characteristic!!.service.uuid.toString(),
                        characteristic.uuid.toString(),
                        value = characteristic.value.toHexString().replace("0x", "")
                    )
                )


            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}