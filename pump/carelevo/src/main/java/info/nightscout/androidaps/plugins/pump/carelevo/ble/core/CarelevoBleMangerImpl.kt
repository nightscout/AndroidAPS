package info.nightscout.androidaps.plugins.pump.carelevo.ble.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleParams
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BondingState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BondingState.Companion.codeToBondingResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CharacterResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.DeviceModuleState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.FailureState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.NotificationState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralConnectionState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralConnectionState.Companion.codeToConnectionResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralScanResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.ScannedDevice
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.ServiceDiscoverState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.existBondedDevice
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.findCharacteristic
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.hasPermission
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.isEnabled
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.isWritable
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.isWritableWithoutResponse
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.refresh
import info.nightscout.androidaps.plugins.pump.carelevo.ble.ext.removeBond
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.util.UUID
import javax.inject.Inject

class CarelevoBleMangerImpl @Inject constructor(
    private val context: Context,
    private val params: BleParams,
    private val aapsLogger: AAPSLogger
) : CarelevoBleManager {

    private val deviceMap = mutableMapOf<String, ScannedDevice>()

    private var isScanning = false
    private var isConnecting = false
    private val stateScope = CoroutineScope(newSingleThreadContext("stateScope"))
    private val commandScope = CoroutineScope(newSingleThreadContext("commandScope"))
    private var bluetoothGatt: BluetoothGatt? = null
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val btLeScanner = btAdapter?.bluetoothLeScanner

    private val defaultScanSetting = ScanSettings.Builder().apply {
        // Avoid method-chaining here: in JVM unit tests Android stubs may return null.
        setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        setReportDelay(0)
        setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
    }.build()

    private var tempAddress: String? = null
    private var disconnectedAddress: String? = null

    @Volatile
    private var isConnectingGatt = false

    private fun defaultBleState() = BleState(
        isEnabled = DeviceModuleState.DEVICE_NONE,
        isConnected = PeripheralConnectionState.CONN_STATE_NONE,
        isBonded = BondingState.BOND_NONE,
        isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
        isNotificationEnabled = NotificationState.NOTIFICATION_NONE
    )

    override fun isBluetoothEnabled(): Boolean {
        return btAdapter?.isEnabled == true
    }

    override fun getBluetoothAdapterState(): Int {
        return btAdapter?.state ?: -1
    }

    override fun isNotificationEnabled(): Boolean {
        if (btAdapter == null) {
            return false
        }
        if (isBluetoothEnabled()) {
            return false
        }

        return bluetoothGatt?.findCharacteristic(params.txUuid)?.let { characteristic ->
            characteristic.getDescriptor(params.cccd)
                ?.takeIf { cccd ->
                    cccd.isEnabled()
                }?.run {
                    true
                }
        } ?: false
    }

    private var isPeripheralRegistered = false

    private fun payloadSummary(payload: ByteArray?): String {
        if (payload == null || payload.isEmpty()) return "len=0"
        val command = payload.first().toUByte().toString(16).padStart(2, '0')
        return "cmd=0x$command len=${payload.size}"
    }

    override fun registerPeripheralInfoRegistered() {
        isPeripheralRegistered = true
    }

    override fun unRegisterPeripheralInfoRegistered() {
        isPeripheralRegistered = false
    }

    fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                && context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    override fun isConnected(macAddress: String): Boolean {
        if (!checkPermissions()) return false
        if (!isBluetoothEnabled()) return false

        val device = btAdapter?.getRemoteDevice(macAddress.uppercase()) ?: return false
        val connectionState = btManager.getConnectionState(device, BluetoothProfile.GATT)
        val gattDevice = bluetoothGatt?.device ?: return false
        val bleState = CarelevoBleSource.bluetoothState.value ?: return false

        aapsLogger.debug(LTag.PUMP, "[CarelevoBleManagerImpl] isConnected.state\n${dumpBleConnectionState(macAddress.uppercase())}")

        return connectionState == BluetoothProfile.STATE_CONNECTED && device == gattDevice
    }

    override fun getGatt(): BluetoothGatt? {
        return bluetoothGatt
    }

    @SuppressLint("MissingPermission")
    override fun clearGatt() {
        bluetoothGatt?.refresh()
        bluetoothGatt?.close()
        bluetoothGatt = null

        val next = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
            isConnected = PeripheralConnectionState.CONN_STATE_NONE,
            isBonded = BondingState.BOND_NONE,
            isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
            isNotificationEnabled = NotificationState.NOTIFICATION_NONE
        )
        CarelevoBleSource._bluetoothState.onNext(next)
    }

    @SuppressLint("MissingPermission")
    override fun isBonded(macAddress: String): Boolean {
        if (btAdapter == null) {
            return false
        }
        if (!checkHasPermission()) {
            return false
        }

        val bondDevice: Set<BluetoothDevice> = btManager.adapter.bondedDevices
        return bondDevice.find { it.address == macAddress.lowercase() || it.address == macAddress.uppercase() } != null
    }

    @SuppressLint("MissingPermission")
    override fun clearBond(macAddress: String): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permission is not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        btAdapter?.bondedDevices
            ?.filter { it.address.lowercase() == macAddress.lowercase() }
            ?.map {
                it.removeBond()
            }
        return CommandResult.Success(true)
    }

    @SuppressLint("MissingPermission")
    override fun disableManager(): Boolean {
        if (btAdapter == null) {
            return false
        }
        if (!isBluetoothEnabled()) {
            return false
        }

        stopScan()
        Thread.sleep(100)
        disconnectFrom()
        Thread.sleep(100)
        bluetoothGatt?.refresh()
        bluetoothGatt?.close()
        Thread.sleep(100)
        bluetoothGatt = null

        CarelevoBleSource.bluetoothState.value?.let { bleState ->
            val currentState = bleState.copy(
                isConnected = PeripheralConnectionState.CONN_STATE_NONE,
                isBonded = BondingState.BOND_NONE,
                isNotificationEnabled = NotificationState.NOTIFICATION_NONE,
                isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE
            )
            CarelevoBleSource._bluetoothState.onNext(currentState)

        }
        return true
    }

    @SuppressLint("MissingPermission")
    override fun startScan(scanFilter: ScanFilter?): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        val scanFilters = scanFilter
            ?.let {
                listOf(it)
            } ?: listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(params.serviceUuid)).build())

        return btLeScanner?.let { scanner ->
            isScanning
                .takeIf {
                    !it
                }?.let {
                    scanner.startScan(scanFilters, defaultScanSetting, scanCallback)
                }

            isScanning = true
            CommandResult.Success(true)
        } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "Scan Device is failed")
    }

    @SuppressLint("MissingPermission")
    override fun stopScan(): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }
        if (!isScanning) {
            return CommandResult.Success(true)
        }

        btLeScanner?.stopScan(scanCallback)
        clearCachedScanDevice()
        isScanning = false
        return CommandResult.Success(true)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectTo(macAddress: String): CommandResult<Boolean> {
        if (macAddress.isEmpty()) {
            return CommandResult.Failure(FailureState.FAILURE_INVALID_PARAMS, "mac address is empty")
        }
        if (btManager == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth manager is not initialized")
        }
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }
        if (isConnecting) {
            return CommandResult.Success(true)
        }
        if (isScanning) {
            stopScan()
        }

        bluetoothGatt?.let {
            it.disconnect()
            it.close()
            bluetoothGatt = null
        }

        tempAddress = macAddress

        val isConnected = isConnected(macAddress)
        if (isConnected) {
            return CommandResult.Success(true)
        }

        val result = runCatching {
            val device = btAdapter?.getRemoteDevice(macAddress)
                ?: return@runCatching CommandResult.Failure(
                    FailureState.FAILURE_COMMAND_NOT_EXECUTABLE,
                    "Remote Device is not found."
                )

            commandScope.async(Dispatchers.Main) {
                bluetoothGatt = device.connectGatt(
                    context.applicationContext,
//                    false,
                    true,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
                delay(1000)
            }.await()

            return@runCatching bluetoothGatt?.let {
                CommandResult.Success(true)
            } ?: CommandResult.Success(false)
        }.getOrElse { e ->
            isConnectingGatt = false
            e.printStackTrace()
            CommandResult.Error(e)
        }
        return result
    }

    @SuppressLint("MissingPermission")
    override fun disconnectFrom(): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        return bluetoothGatt?.run {
            isConnectingGatt = false
            disconnect()
            close()
            bluetoothGatt = null

            val currentState = CarelevoBleSource.bluetoothState.value?.copy(
                isConnected = PeripheralConnectionState.CONN_STATE_NONE,
                isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
                isNotificationEnabled = NotificationState.NOTIFICATION_NONE
            )

            if (currentState != null) {
                CarelevoBleSource._bluetoothState.onNext(currentState)
                CommandResult.Success(true)
            } else {
                CommandResult.Success(false)
            }
        } ?: CommandResult.Success(false)
    }

    @SuppressLint("MissingPermission")
    override fun discoverService(): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        return bluetoothGatt?.run {
            if (discoverServices()) {
                CommandResult.Success(true)
            } else {
                CommandResult.Success(false)
            }
        } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "not found bluetooth gatt")
    }

    override fun unBondDevice(macAddress: String): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        return runCatching {
            btManager.existBondedDevice(macAddress)
        }.fold(
            onSuccess = { isDeviceFound ->
                if (isDeviceFound) {
                    val actionResult = bluetoothGatt?.device?.removeBond()
                    actionResult?.let {
                        val currentState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(isBonded = BondingState.BOND_NONE)
                        CarelevoBleSource._bluetoothState.onNext(currentState)
                        CommandResult.Success(actionResult)
                    } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "remote device is not found")
                } else {
                    CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "remote device is not found")
                }
            },
            onFailure = {
                it.printStackTrace()
                CommandResult.Error(it)
            }
        )
    }

    @SuppressLint("MissingPermission")
    override fun writeCharacteristic(uuid: UUID, payload: ByteArray): CommandResult<Boolean> {
        val bleState = CarelevoBleSource.bluetoothState.value

        if (bleState?.isServiceDiscovered != ServiceDiscoverState.DISCOVER_STATE_DISCOVERED) {
            clearGatt()
            disconnectedAddress?.let { address ->
                stateScope.launch {
                    delay(2_000L)
                    aapsLogger.debug(LTag.PUMP, "[CarelevoBleManagerImpl] writeCharacteristic.reconnect address=$address")
                    connectTo(address)
                }
            }
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }

        if (btAdapter == null) {
            return CommandResult.Failure(
                FailureState.FAILURE_RESOURCE_NOT_INITIALIZED,
                "bluetooth adapter is not initialized"
            )
        }

        if (!checkHasPermission()) {
            return CommandResult.Failure(
                FailureState.FAILURE_PERMISSION_NOT_GRANTED,
                "permissions are not granted"
            )
        }

        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(
                FailureState.FAILURE_BT_NOT_ENABLED,
                "bluetooth is not enabled"
            )
        }

        if (isConnectingGatt) {
            aapsLogger.warn(LTag.PUMP, "[CarelevoBleManagerImpl] writeCharacteristic.blocked reconnecting=true")
            return CommandResult.Failure(
                FailureState.FAILURE_COMMAND_NOT_EXECUTABLE,
                "Reconnecting"
            )
        }

        val gatt = bluetoothGatt
            ?: return CommandResult.Failure(
                FailureState.FAILURE_COMMAND_NOT_EXECUTABLE,
                "BluetoothGatt is null"
            )

        if (gatt.services.isNullOrEmpty()) {
            aapsLogger.error(
                LTag.PUMP,
                "[CarelevoBleManagerImpl] writeCharacteristic.blocked servicesDiscovered=false gatt=${gatt.hashCode()}"
            )
            return CommandResult.Failure(
                FailureState.FAILURE_COMMAND_NOT_EXECUTABLE,
                "Services not discovered yet"
            )
        }

        if (gatt !== bluetoothGatt) {
            aapsLogger.warn(
                LTag.PUMP,
                "[CarelevoBleManagerImpl] writeCharacteristic.blocked staleGatt=${gatt.hashCode()} currentGatt=${bluetoothGatt?.hashCode()}"
            )
            return CommandResult.Failure(
                FailureState.FAILURE_COMMAND_NOT_EXECUTABLE,
                "Stale GATT"
            )
        }

        aapsLogger.debug(
            LTag.PUMP,
            "[CarelevoBleManagerImpl] writeCharacteristic.outgoing ${payloadSummary(payload)} gatt=${gatt.hashCode()}"
        )

        return gatt.findCharacteristic(params.rxUUID)?.let { characteristicTarget ->
            aapsLogger.debug(
                LTag.PUMP,
                "[CarelevoBleManagerImpl] writeCharacteristic.target uuid=${characteristicTarget.uuid} gatt=${gatt.hashCode()}"
            )

            val writeType = when {
                characteristicTarget.isWritable() -> {
                    aapsLogger.debug(LTag.PUMP, "[CarelevoBleManagerImpl] writeCharacteristic.mode withResponse=true")
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }

                characteristicTarget.isWritableWithoutResponse() -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }

                else -> {
                    return CommandResult.Failure(
                        FailureState.FAILURE_COMMAND_NOT_EXECUTABLE,
                        "Characteristic target is not writeable"
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (gatt.writeCharacteristic(characteristicTarget, payload, writeType)
                    == BluetoothStatusCodes.SUCCESS
                ) {
                    CommandResult.Success(true)
                } else {
                    CommandResult.Success(false)
                }
            } else {
                characteristicTarget.writeType = writeType
                characteristicTarget.value = payload
                if (gatt.writeCharacteristic(characteristicTarget)) {
                    CommandResult.Success(true)
                } else {
                    CommandResult.Success(false)
                }
            }
        } ?: CommandResult.Failure(
            FailureState.FAILURE_COMMAND_NOT_EXECUTABLE,
            "not found characteristic of ${params.rxUUID}"
        )
    }

    @SuppressLint("MissingPermission")
    override fun readCharacteristic(characteristicUuid: UUID): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        return bluetoothGatt?.let { gatt ->
            gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                if (gatt.readCharacteristic(characteristic)) {
                    CommandResult.Success(true)
                } else {
                    CommandResult.Success(false)
                }
            } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "not found characteristic of $characteristicUuid")
        } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "bluetooth is not connected")
    }

    @SuppressLint("MissingPermission")
    override fun enabledNotifications(uuid: UUID): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        val gatt = bluetoothGatt ?: return CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "bluetooth is not connected")
        if (gatt.services.isNullOrEmpty()) {
            aapsLogger.error(
                LTag.PUMP,
                "[CarelevoBleManagerImpl] enabledNotifications.blocked servicesDiscovered=false gatt=${gatt.hashCode()}"
            )
            return CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "Services not discovered")
        }

        return bluetoothGatt?.let { gatt ->
            gatt.findCharacteristic(params.txUuid)?.run {
                getDescriptor(params.cccd)?.let { cccDescriptor ->
                    if (!gatt.setCharacteristicNotification(this, true)) {
                        CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "set characteristic notification failed for ${this.uuid}")
                    } else {
                        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (gatt.writeDescriptor(cccDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS) {
                                CommandResult.Success(true)
                            } else {
                                CommandResult.Success(false)
                            }
                        } else {
                            cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (gatt.writeDescriptor(cccDescriptor)) {
                                CommandResult.Success(true)
                            } else {
                                CommandResult.Success(false)
                            }
                        }
                        /*val currentState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                            isNotificationEnabled = NotificationState.NOTIFICATION_ENABLED
                        )*/
                        //CarelevoBleSource._bluetoothState.onNext(currentState)
                        success
                    }
                } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "not found descriptor ${params.cccd}")
            } ?: run {
                CoroutineScope(Dispatchers.IO).launch {
                    discoverService()
                }
                CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "not found characteristic of ${params.txUuid}")
            }
        } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "bluetooth is not connected")
    }

    @SuppressLint("MissingPermission")
    override fun disabledNotifications(uuid: UUID): CommandResult<Boolean> {
        if (btAdapter == null) {
            return CommandResult.Failure(FailureState.FAILURE_RESOURCE_NOT_INITIALIZED, "bluetooth adapter is not initialized")
        }
        if (!checkHasPermission()) {
            return CommandResult.Failure(FailureState.FAILURE_PERMISSION_NOT_GRANTED, "permissions are not granted")
        }
        if (!isBluetoothEnabled()) {
            return CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth is not enabled")
        }

        return bluetoothGatt?.let { gatt ->
            gatt.findCharacteristic(uuid)?.let { characteristic ->
                characteristic.getDescriptor(params.cccd)
                    ?.takeIf {
                        it.isEnabled()
                    }?.let { cccDescriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, false)) {
                            CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "set characteristic notification failed for ${characteristic.uuid}")
                        } else {
                            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (gatt.writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS) {
                                    CommandResult.Success(true)
                                } else {
                                    CommandResult.Success(false)
                                }
                            } else {
                                cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                if (gatt.writeDescriptor(cccDescriptor)) {
                                    CommandResult.Success(true)
                                } else {
                                    CommandResult.Success(false)
                                }
                            }
                            val currentState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                                isNotificationEnabled = if (success.data) NotificationState.NOTIFICATION_DISABLED else NotificationState.NOTIFICATION_ENABLED
                            )
                            CarelevoBleSource._bluetoothState.onNext(currentState)
                            success
                        }
                    } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "not found descriptor of ${params.cccd}")
            } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "not found characteristic of $uuid")
        } ?: CommandResult.Failure(FailureState.FAILURE_COMMAND_NOT_EXECUTABLE, "bluetooth is not connected")
    }

    private fun clearCachedScanDevice() {
        deviceMap.clear()
    }

    private fun checkHasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isScanPermissionGranted = context.hasPermission(Manifest.permission.BLUETOOTH_SCAN)
            val isBluetoothConnectPermissionGranted = context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

            isScanPermissionGranted && isBluetoothConnectPermissionGranted
        } else {
            context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            val isFineLocationPermissionGranted = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            isFineLocationPermissionGranted
        }
    }

    //=============================================================================
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (!checkHasPermission()) {
                return
            }
            result?.let { scanDevice ->
                deviceMap
                    .getOrPut(scanDevice.device.address) {
                        ScannedDevice(
                            scanDevice.device,
                            scanDevice.rssi
                        )
                    }.takeIf {
                        it.rssi < scanDevice.rssi
                    }?.let {
                        deviceMap[scanDevice.device.address] = ScannedDevice(scanDevice.device, scanDevice.rssi)
                    }
            }
            CarelevoBleSource._scanDevices.onNext(
                PeripheralScanResult.Success(
                    deviceMap.values.toList().sortedByDescending { it.rssi }
                )
            )
        }

    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            aapsLogger.warn(
                LTag.PUMP,
                "[CarelevoBleManagerImpl] onConnectionStateChange gatt=${gatt?.hashCode()} status=$status newState=$newState services=${gatt?.services?.size}"
            )

            var currentState: BleState? = CarelevoBleSource.bluetoothState.value?.copy()
            val bondState = gatt?.device?.bondState ?: -1
            val notificationState = if (isNotificationEnabled()) {
                1
            } else {
                0
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {

                        currentState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                            isConnected = newState.codeToConnectionResult(),
                            isBonded = bondState.codeToBondingResult(),
                            isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
                            isNotificationEnabled = NotificationState.NOTIFICATION_NONE
                        )
                        CarelevoBleSource._bluetoothState.onNext(currentState)

                        gatt?.device?.createBond()
                        if (Build.VERSION.SDK_INT >= 34) {
                            gatt?.discoverServices()
                        } else {
                            if ((gatt?.getService(params.serviceUuid) == null) && !isPeripheralRegistered) {
                                if (gatt?.device?.bondState == BluetoothDevice.BOND_BONDED) {
                                    gatt.discoverServices()
                                }
                            } else {
                                enabledNotifications(params.txUuid)
                            }
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        currentState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                            isConnected = newState.codeToConnectionResult(),
                            isBonded = bondState.codeToBondingResult(),
                            isNotificationEnabled = NotificationState.NOTIFICATION_DISABLED,
                            isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_CLEARED
                        )
                        CarelevoBleSource._bluetoothState.onNext(currentState)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    aapsLogger.debug(LTag.PUMP, "[CarelevoBleManagerImpl] onConnectionStateChange.disconnected status=$status")
                    disconnectedAddress = gatt?.device?.address
                    isConnectingGatt = false
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            if (CarelevoBleSource._bluetoothState.value?.isEnabled != DeviceModuleState.DEVICE_STATE_ON) {
                                gatt?.disconnect()
                                gatt?.refresh()
                                gatt?.close()
                                bluetoothGatt = null
                            }
                            currentState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                                isConnected = PeripheralConnectionState.CONN_STATE_NONE,
                                isBonded = BondingState.BOND_NONE,
                                isNotificationEnabled = NotificationState.NOTIFICATION_NONE,
                                isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE
                            )
                            CarelevoBleSource._bluetoothState.onNext(currentState)
                        }

                        22 -> {
                            gatt?.close()
                            bluetoothGatt = null

                            val nextState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                                isConnected = PeripheralConnectionState.CONN_STATE_DISCONNECTED,
                                isBonded = BondingState.BOND_NONE,
                                isNotificationEnabled = NotificationState.NOTIFICATION_NONE,
                                isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE
                            )

                            CarelevoBleSource._bluetoothState.onNext(nextState)
                            disconnectedAddress?.let { address ->
                                stateScope.launch {
                                    delay(2_000L)
                                    connectTo(address)
                                }
                            }
                        }

                        else -> {

                            if (CarelevoBleSource.bluetoothState.value?.isEnabled != DeviceModuleState.DEVICE_STATE_ON) {
                                gatt?.disconnect()
                                gatt?.refresh()
                                gatt?.close()
                                bluetoothGatt = null
                            }

                            currentState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                                isConnected = PeripheralConnectionState.CONN_STATE_DISCONNECTED,
                                isBonded = BondingState.BOND_NONE,
                                isNotificationEnabled = NotificationState.NOTIFICATION_NONE,
                                isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE
                            )
                            CarelevoBleSource._bluetoothState.onNext(currentState)
                        }
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) return

            aapsLogger.warn(
                LTag.PUMP,
                "[CarelevoBleManagerImpl] onServicesDiscovered gatt=${gatt.hashCode()} status=$status services=${gatt.services.size}"
            )

            if (bluetoothGatt != null && bluetoothGatt !== gatt) {
                aapsLogger.warn(LTag.PUMP, "[CarelevoBleManagerImpl] onServicesDiscovered.ignored staleGatt=${gatt.hashCode()}")
                gatt.close()
                return
            }

            //bluetoothGatt = gatt
            isConnectingGatt = false
            aapsLogger.debug(LTag.PUMP, "[CarelevoBleManagerImpl] onServicesDiscovered.activeGatt gatt=${gatt.hashCode()}")

            val nextState = (CarelevoBleSource.bluetoothState.value ?: defaultBleState())
                .copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED)

            CarelevoBleSource._bluetoothState.onNext(nextState)

            enabledNotifications(params.txUuid)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            aapsLogger.debug(LTag.PUMP, "[CarelevoBleManagerImpl] onCharacteristicChanged.incoming ${payloadSummary(value)}")
            CarelevoBleSource._notifyIndicateBytes.onNext(
                CharacterResult(
                    uuidCharacteristic = characteristic.uuid,
                    value = value
                )
            )

            CarelevoBleSource._bluetoothState.onNext(
                (CarelevoBleSource.bluetoothState.value ?: defaultBleState()).copy(
                    isNotificationEnabled = NotificationState.NOTIFICATION_ENABLED
                )
            )
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                aapsLogger.debug(
                    LTag.PUMP,
                    "[CarelevoBleManagerImpl] onCharacteristicChangedDeprecated.incoming ${payloadSummary(characteristic?.value)}"
                )
                CarelevoBleSource._notifyIndicateBytes.onNext(
                    CharacterResult(
                        uuidCharacteristic = characteristic?.uuid,
                        value = characteristic?.value
                    )
                )
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            CoroutineScope(CoroutineName("onCharacteristicRead")).launch {
                // CarelevoRxBleSource._readBytes.emit(
                //     CharacterResult(
                //         uuidCharacteristic = characteristic.uuid,
                //         value = value
                //     )
                // )
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            CoroutineScope(CoroutineName("onCharacteristicRead")).launch {
                // CarelevoBleSource._readBytes.emit(
                //     CharacterResult(
                //         uuidCharacteristic = characteristic?.uuid,
                //         value = characteristic?.value
                //     )
                // )
            }
        }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun dumpBleConnectionState(macAddress: String): String {
        val sb = StringBuilder()

        // 1. Bluetooth ON
        sb.appendLine("1. Bluetooth enabled = ${isBluetoothEnabled()}")

        // 2. Permission
        sb.appendLine("2. Permission OK = ${checkPermissions()}")

        val device = btAdapter?.getRemoteDevice(macAddress.uppercase())
        val connState = device?.let {
            btManager.getConnectionState(it, BluetoothProfile.GATT)
        }
        sb.appendLine("3. GATT connectionState = $connState (CONNECTED=2)")

        sb.appendLine("4. bluetoothGatt != null = ${bluetoothGatt != null}")

        sb.appendLine(
            "5. gattDevice match = ${
                bluetoothGatt?.device?.address == macAddress.uppercase()
            }"
        )

        // 6. Service discovered
        val bleState = CarelevoBleSource.bluetoothState.value
        sb.appendLine(
            "6. Service discovered = ${
                bleState?.isServiceDiscovered == ServiceDiscoverState.DISCOVER_STATE_DISCOVERED
            }"
        )

        sb.appendLine(
            "7. GATT services count = ${bluetoothGatt?.services?.size ?: 0}"
        )

        // 8. Notification enabled
        sb.appendLine(
            "8. Notification enabled = ${
                bleState?.isNotificationEnabled == NotificationState.NOTIFICATION_ENABLED
            }"
        )

        // 9. isConnectingGatt
        sb.appendLine("9. isConnectingGatt = $isConnectingGatt")

        return sb.toString()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun forceBleResetAndReconnect(address: String) {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.refresh()
        bluetoothGatt?.close()
        bluetoothGatt = null

        isConnectingGatt = false
        CarelevoBleSource._bluetoothState.onNext(defaultBleState())

        stateScope.launch {
            delay(1000)
            connectTo(address)
        }
    }
}
