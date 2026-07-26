package app.aaps.pump.medtrum.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.medtrum.comm.ManufacturerData
import app.aaps.pump.medtrum.comm.ReadDataPacket
import app.aaps.pump.medtrum.comm.WriteCommandPackets
import app.aaps.pump.medtrum.extension.toInt
import app.aaps.pump.medtrum.keys.MedtrumBooleanKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class MedtrumBleTransportImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    private val preferences: Preferences,
    private val rxBus: RxBus
) : MedtrumBleTransport {

    companion object {

        private const val WRITE_DELAY_MILLIS = 10L
        private const val SERVICE_UUID = "669A9001-0008-968F-E311-6050405558B3"
        private const val READ_UUID = "669a9120-0008-968f-e311-6050405558b3"
        private const val WRITE_UUID = "669a9101-0008-968f-e311-6050405558b3"
        private const val CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val NEEDS_ENABLE_NOTIFICATION = 0x10
        private const val NEEDS_ENABLE_INDICATION = 0x20
        private const val NEEDS_ENABLE = 0x30
        private const val MANUFACTURER_ID = 18305
    }

    private val handler = Handler(HandlerThread("MedtrumBleHandler").also { it.start() }.looper)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    // GATT state
    private var bluetoothGatt: BluetoothGatt? = null
    private var uartRead: BluetoothGattCharacteristic? = null
    private var uartWrite: BluetoothGattCharacteristic? = null

    // Connection tracking
    private var isConnected = false
    private var isConnecting = false

    // Write/read packet reassembly
    private var writePackets: WriteCommandPackets? = null
    private var writeSequenceNumber = 0
    private var readPacket: ReadDataPacket? = null
    private val readLock = Any()

    // Cached device for reconnection (in-memory, cleared on app restart → triggers new scan)
    private var cachedDeviceAddress: String? = null
    private var cachedDeviceSN: Long = 0

    // Address pre-seeded from wizard BLE scan selection (consumed on first connect)
    private var wizardSelectedAddress: String? = null

    // Callbacks / listeners
    private var medtrumCallback: MedtrumBleCallback? = null
    private var transportListener: BleTransportListener? = null

    // Connection scan callback (auto-connects on SN match)
    private var connectionScanCallback: ScanCallback? = null

    // --- BleTransport ---

    override val adapter: BleAdapter = MedtrumAdapterImpl()
    override val scanner: BleScanner = MedtrumScannerImpl()
    override val gatt: BleGatt = MedtrumGattImpl()

    private val _pairingState = MutableStateFlow(PairingState())
    override val pairingState: StateFlow<PairingState> = _pairingState

    override fun updatePairingState(state: PairingState) {
        _pairingState.value = state
    }

    override fun setListener(listener: BleTransportListener?) {
        transportListener = listener
    }

    // --- MedtrumBleTransport ---

    override fun setMedtrumCallback(callback: MedtrumBleCallback?) {
        medtrumCallback = callback
    }

    override fun setCachedAddress(address: String) {
        wizardSelectedAddress = address
    }

    @Synchronized
    override fun connect(from: String, deviceSN: Long): Boolean {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) || !hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            rxBus.send(EventShowSnackbar(context.getString(app.aaps.core.ui.R.string.need_connect_permission), EventShowSnackbar.Type.Error))
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return false
        }
        if (bluetoothAdapter == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "no BluetoothAdapter: $from")
            return false
        }

        isConnected = false
        isConnecting = true
        writePackets = null
        readPacket = null

        val wizardAddr = wizardSelectedAddress?.also { wizardSelectedAddress = null }
        when {
            wizardAddr != null                                        -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Using wizard-selected address: $wizardAddr")
                cachedDeviceAddress = wizardAddr
                cachedDeviceSN = deviceSN
                bluetoothAdapter?.getRemoteDevice(wizardAddr)?.let { connectGatt(it) }
            }

            cachedDeviceAddress != null && cachedDeviceSN == deviceSN -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Skipping scan, connecting directly to cached address")
                bluetoothAdapter?.getRemoteDevice(cachedDeviceAddress)?.let { connectGatt(it) }
            }

            else                                                      -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "No cached address, scanning for deviceSN: $deviceSN")
                cachedDeviceAddress = null
                cachedDeviceSN = deviceSN
                startConnectionScan(deviceSN)
            }
        }
        return true
    }

    @Synchronized
    override fun disconnect(from: String) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")
        if (isConnecting) {
            isConnecting = false
            stopConnectionScan()
            SystemClock.sleep(100)
        }
        if (isConnected) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Connected, disconnecting")
            bluetoothGatt?.disconnect()
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Not connected, closing gatt")
            gatt.close()
            isConnected = false
            medtrumCallback?.onDisconnected()
        }
    }

    @Synchronized
    override fun sendMessage(message: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "sendMessage: ${message.contentToString()}")
        if (writePackets?.allPacketsConsumed() == false) {
            aapsLogger.error(LTag.PUMPBTCOMM, "sendMessage: previous packets not consumed, dropping")
            return
        }
        writePackets = WriteCommandPackets(message, writeSequenceNumber)
        writeSequenceNumber = (writeSequenceNumber + 1) % 256
        val first = writePackets?.getNextPacket()
        if (first != null) {
            writeCharacteristicInternal(uartWriteChar, first)
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "sendMessage: error building write packet")
            medtrumCallback?.onSendMessageError("error in writePacket!", false)
        }
    }

    // --- GATT callback ---

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onConnectionStateChangeSynchronized(gatt, status, newState)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) findCharacteristic()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicChanged UUID: ${characteristic.uuid}")
            val value = characteristic.value
            when (characteristic.uuid) {
                UUID.fromString(READ_UUID)  -> medtrumCallback?.onNotification(value)
                UUID.fromString(WRITE_UUID) -> handleIndication(value)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicWrite status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writePackets?.let { packets ->
                    synchronized(packets) {
                        val next = packets.getNextPacket()
                        if (next != null) writeCharacteristicInternal(uartWriteChar, next)
                    }
                }
            } else {
                medtrumCallback?.onSendMessageError("onCharacteristicWrite failure", true)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorWrite status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) readDescriptor(descriptor)
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorRead status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) checkDescriptor(descriptor)
        }
    }

    // --- Internal helpers ---

    private fun handleIndication(value: ByteArray) {
        synchronized(readLock) {
            if (readPacket == null) {
                readPacket = ReadDataPacket(value)
            } else {
                readPacket?.addData(value)
            }
            if (readPacket?.allDataReceived() == true) {
                if (readPacket?.failed() == true) {
                    medtrumCallback?.onSendMessageError("ReadDataPacket failed", false)
                } else {
                    readPacket?.getData()?.let { medtrumCallback?.onIndication(it) }
                }
                readPacket = null
            }
        }
    }

    @Synchronized
    private fun connectGatt(device: BluetoothDevice) {
        writeSequenceNumber = 0
        if (bluetoothGatt == null) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "connectGatt: gatt already exists, disconnecting first")
            disconnect("connectGatt conflict")
        }
    }

    @Synchronized
    private fun onConnectionStateChangeSynchronized(gatt: BluetoothGatt, status: Int, newState: Int) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange newState: $newState status: $status")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            isConnected = true
            isConnecting = false
            bluetoothGatt?.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (isConnecting) {
                val resetDevice = preferences.get(MedtrumBooleanKey.MedtrumScanOnConnectionErrors)
                if (resetDevice) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "Disconnected while connecting, resetting cached address")
                    cachedDeviceAddress = null
                }
                SystemClock.sleep(2000)
            }
            gatt.close()
            bluetoothGatt = null
            uartRead = null
            uartWrite = null
            isConnected = false
            isConnecting = false
            medtrumCallback?.onDisconnected()
            aapsLogger.debug(LTag.PUMPBTCOMM, "Device disconnected: ${gatt.device.name}")
        }
    }

    @Suppress("DEPRECATION")
    @Synchronized
    private fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic?, enabled: Boolean) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            handleNotInitialized(); return
        }
        bluetoothGatt?.setCharacteristicNotification(characteristic, enabled)
        characteristic?.getDescriptor(UUID.fromString(CONFIG_UUID))?.let {
            when {
                characteristic.properties and NEEDS_ENABLE_NOTIFICATION > 0 -> {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt?.writeDescriptor(it)
                }

                characteristic.properties and NEEDS_ENABLE_INDICATION > 0   -> {
                    it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    bluetoothGatt?.writeDescriptor(it)
                }
            }
        }
    }

    @Synchronized
    private fun readDescriptor(descriptor: BluetoothGattDescriptor?) {
        if (bluetoothAdapter == null || bluetoothGatt == null || descriptor == null) {
            handleNotInitialized(); return
        }
        bluetoothGatt?.readDescriptor(descriptor)
    }

    @Suppress("DEPRECATION")
    private fun checkDescriptor(descriptor: BluetoothGattDescriptor) {
        val service = getGattService() ?: return
        if (descriptor.value.toInt() <= 0) return
        val allEnabled = service.characteristics.all { char ->
            val cfg = char.getDescriptor(UUID.fromString(CONFIG_UUID))
            cfg?.value != null && cfg.value.toInt() > 0
        }
        if (allEnabled) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "All notifications enabled, connected!")
            cachedDeviceAddress = bluetoothGatt?.device?.address
            medtrumCallback?.onConnected()
        }
    }

    private fun findCharacteristic() {
        val service = getGattService() ?: return
        service.characteristics.forEachIndexed { i, char ->
            if (char.properties and NEEDS_ENABLE > 0) {
                handler.postDelayed({
                                        val uuid = char.uuid.toString()
                                        setCharacteristicNotification(char, true)
                                        if (READ_UUID == uuid) uartRead = char
                                        if (WRITE_UUID == uuid) uartWrite = char
                                    }, i * 600L)
            }
        }
    }

    private fun getGattService(): BluetoothGattService? {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            handleNotInitialized(); return null
        }
        return bluetoothGatt?.getService(UUID.fromString(SERVICE_UUID))
    }

    @Suppress("DEPRECATION")
    @Synchronized
    private fun writeCharacteristicInternal(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        handler.postDelayed({
                                if (bluetoothAdapter == null || bluetoothGatt == null) {
                                    handleNotInitialized()
                                } else {
                                    characteristic.value = data
                                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "writeCharacteristic: ${data.contentToString()}")
                                    if (bluetoothGatt?.writeCharacteristic(characteristic) != true) {
                                        medtrumCallback?.onSendMessageError("Failed to write characteristic", true)
                                    }
                                }
                            }, WRITE_DELAY_MILLIS)
    }

    private val uartWriteChar: BluetoothGattCharacteristic
        get() = uartWrite ?: BluetoothGattCharacteristic(UUID.fromString(WRITE_UUID), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, 0).also { uartWrite = it }

    private fun handleNotInitialized() {
        aapsLogger.error(LTag.PUMPBTCOMM, "BluetoothAdapter or Gatt not initialized")
        isConnecting = false
        isConnected = false
    }

    // --- Connection scan (auto-connects on SN match) ---

    @Synchronized
    private fun startConnectionScan(deviceSN: Long) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        aapsLogger.debug(LTag.PUMPBTCOMM, "startConnectionScan for SN: $deviceSN")
        connectionScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val mfData = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)?.let { ManufacturerData(it) }
                aapsLogger.debug(LTag.PUMPBTCOMM, "ConnectionScan found SN: ${mfData?.getDeviceSN()}")
                if (mfData?.getDeviceSN() == deviceSN) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Found target device! SN: ${mfData.getDeviceSN()}")
                    stopConnectionScan()
                    cachedDeviceAddress = result.device.address
                    connectGatt(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Connection scan failed: $errorCode")
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val filters = listOf(ScanFilter.Builder().setDeviceName("MT").build())
        try {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(filters, settings, connectionScanCallback)
        } catch (_: IllegalStateException) { /* BT off */
        }
    }

    @Synchronized
    private fun stopConnectionScan() {
        try {
            connectionScanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
        } catch (_: IllegalStateException) { /* BT off */
        }
        connectionScanCallback = null
    }

    private fun hasPermission(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // --- BleAdapter ---

    private inner class MedtrumAdapterImpl : BleAdapter {

        override fun enable() = Unit

        override fun getDeviceName(address: String): String? {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return null
            return bluetoothAdapter?.getRemoteDevice(address)?.name
        }

        override fun isDeviceBonded(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            return bluetoothAdapter?.getRemoteDevice(address)?.bondState != BluetoothDevice.BOND_NONE
        }

        override fun createBond(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            return bluetoothAdapter?.getRemoteDevice(address)?.createBond() == true
        }

        override fun removeBond(address: String) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            try {
                bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address }?.let {
                    it.javaClass.getMethod("removeBond").invoke(it)
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Error removing bond", e)
            }
        }
    }

    // --- BleScanner (discovery — used by wizard BLE scan step) ---

    private inner class MedtrumScannerImpl : BleScanner {

        private var scanCallback: ScanCallback? = null
        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 10)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        override fun startScan() {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.device?.name ?: return
                    val mfData = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)?.let { ManufacturerData(it) }
                    val sn = mfData?.getDeviceSN()
                    val displayName = if (sn != null && sn > 0) "MT-${sn.toString(16).uppercase()}" else name
                    _scannedDevices.tryEmit(
                        ScannedDevice(
                            name = displayName,
                            address = result.device.address,
                            scanRecordBytes = result.scanRecord?.bytes
                        )
                    )
                }

                override fun onScanFailed(errorCode: Int) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Discovery scan failed: $errorCode")
                }
            }
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            val filters = listOf(ScanFilter.Builder().setDeviceName("MT").build())
            try {
                bluetoothAdapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            } catch (_: IllegalStateException) { /* BT off */
            }
        }

        override fun stopScan() {
            try {
                scanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
            } catch (_: IllegalStateException) { /* BT off */
            }
            scanCallback = null
        }
    }

    // --- BleGatt ---

    private inner class MedtrumGattImpl : BleGatt {

        override fun connect(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            connectGatt(device)
            return true
        }

        override fun disconnect() {
            bluetoothGatt?.disconnect()
        }

        override fun close() {
            bluetoothGatt?.close()
            SystemClock.sleep(100)
            bluetoothGatt = null
            uartRead = null
            uartWrite = null
        }

        override fun discoverServices() {
            bluetoothGatt?.discoverServices()
        }

        override fun findCharacteristics(): Boolean = uartRead != null && uartWrite != null

        override fun enableNotifications() {
            // Handled internally via findCharacteristic() after services are discovered
        }

        override fun writeCharacteristic(data: ByteArray) {
            writeCharacteristicInternal(uartWriteChar, data)
        }
    }
}
