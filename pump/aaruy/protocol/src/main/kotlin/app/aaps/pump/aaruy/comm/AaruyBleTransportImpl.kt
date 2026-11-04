package app.aaps.pump.aaruy.comm

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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque
import java.util.UUID

/**
 * The radio: Android's `BluetoothGatt` behind [AaruyBleTransport].
 *
 * Android runs one GATT operation at a time and refuses a new one while another is pending. A pump
 * message can make the client send a command during a write, so reads and writes wait in [operations].
 */
@SuppressLint("MissingPermission")
class AaruyBleTransportImpl(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : AaruyBleTransport {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private var listener: BleTransportListener? = null
    private var extras: AaruyTransportExtras? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var firmwareCharacteristic: BluetoothGattCharacteristic? = null

    private class Operation(val isWrite: Boolean, val start: () -> Boolean)

    private val operations = ArrayDeque<Operation>()
    private var current: Operation? = null

    private fun enqueue(operation: Operation) {
        synchronized(operations) {
            operations.addLast(operation)
            startNext()
        }
    }

    /** The caller holds the lock on [operations]. */
    private fun startNext() {
        while (current == null) {
            val next = operations.pollFirst() ?: return
            current = next
            if (!next.start()) {
                aapsLogger.error(LTag.PUMPBTCOMM, "GATT operation could not start")
                current = null
                if (next.isWrite) extras?.onWriteFailed()
            }
        }
    }

    private fun finishCurrent() {
        synchronized(operations) {
            current = null
            startNext()
        }
    }

    private fun failAllOperations() {
        val failedWrites: Int
        synchronized(operations) {
            failedWrites = operations.count { it.isWrite } + if (current?.isWrite == true) 1 else 0
            operations.clear()
            current = null
        }
        repeat(failedWrites) { extras?.onWriteFailed() }
    }

    @Suppress("deprecation", "OVERRIDE_DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange status=$status state=$newState")
            val connected = status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED
            if (!connected) failAllOperations()
            listener?.onConnectionStateChanged(connected)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val pumpService = gatt.getService(SERVICE_PUMP)
            if (status != BluetoothGatt.GATT_SUCCESS || pumpService == null) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Pump service not found, status=$status")
                listener?.onServicesDiscovered(false)
                return
            }
            notifyCharacteristic = pumpService.getCharacteristic(CHARACTERISTIC_NOTIFY)
            writeCharacteristic = pumpService.getCharacteristic(CHARACTERISTIC_WRITE)
            firmwareCharacteristic = gatt.getService(SERVICE_DEVICE_INFO)?.getCharacteristic(CHARACTERISTIC_FIRMWARE)
            listener?.onServicesDiscovered(notifyCharacteristic != null && writeCharacteristic != null)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            extras?.onMtuChanged(mtu)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) listener?.onDescriptorWritten()
            else aapsLogger.error(LTag.PUMPBTCOMM, "Notifications not enabled, status=$status")
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) listener?.onCharacteristicWritten() else extras?.onWriteFailed()
            finishCurrent()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value?.let { extras?.onFirmwareVersionRead(it) }
            finishCurrent()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) extras?.onFirmwareVersionRead(value)
            finishCurrent()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { listener?.onCharacteristicChanged(it) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            listener?.onCharacteristicChanged(value)
        }
    }

    override val adapter: BleAdapter = object : BleAdapter {
        override fun enable() {}

        override fun getDeviceName(address: String): String? {
            if (!hasConnectPermission()) return null
            return bluetoothAdapter?.getRemoteDevice(address)?.name
        }

        override fun isDeviceBonded(address: String): Boolean {
            if (!hasConnectPermission()) return false
            return bluetoothAdapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
        }

        override fun createBond(address: String): Boolean {
            if (!hasConnectPermission()) return false
            return bluetoothAdapter?.getRemoteDevice(address)?.createBond() == true
        }

        /** `removeBond` is hidden in the Android API, so it can only be reached by reflection. */
        override fun removeBond(address: String) {
            if (!hasConnectPermission()) return
            try {
                bluetoothAdapter?.getRemoteDevice(address)?.let { it.javaClass.getMethod("removeBond").invoke(it) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "removeBond failed", e)
            }
        }
    }

    /** Scanning for pumps is in [AaruyScanner]. */
    override val scanner: BleScanner = object : BleScanner {
        override val scannedDevices: SharedFlow<ScannedDevice> = MutableSharedFlow()
        override fun startScan() {}
        override fun stopScan() {}
    }

    @Suppress("deprecation")
    override val gatt: BleGatt = object : BleGatt {
        override fun connect(address: String): Boolean {
            if (!hasConnectPermission()) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            // Android limits the GATT clients. Leaking them ends in status 133 and no connection at all.
            close()
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            return bluetoothGatt != null
        }

        override fun disconnect() {
            bluetoothGatt?.disconnect()
        }

        override fun close() {
            bluetoothGatt?.close()
            bluetoothGatt = null
            notifyCharacteristic = null
            writeCharacteristic = null
            firmwareCharacteristic = null
            failAllOperations()
        }

        override fun discoverServices() {
            bluetoothGatt?.discoverServices()
        }

        override fun findCharacteristics(): Boolean = notifyCharacteristic != null && writeCharacteristic != null

        override fun enableNotifications() {
            val gatt = bluetoothGatt ?: return
            val characteristic = notifyCharacteristic ?: return
            val descriptor = characteristic.getDescriptor(CCCD) ?: return
            gatt.setCharacteristicNotification(characteristic, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun writeCharacteristic(data: ByteArray) {
            enqueue(Operation(isWrite = true) { startWrite(data) })
        }
    }

    @Suppress("deprecation")
    private fun startWrite(data: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val characteristic = writeCharacteristic ?: return false
        val type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return gatt.writeCharacteristic(characteristic, data, type) == BluetoothStatusCodes.SUCCESS
        }
        characteristic.writeType = type
        characteristic.value = data
        return gatt.writeCharacteristic(characteristic)
    }

    override fun requestMtu(mtu: Int) {
        if (bluetoothGatt?.requestMtu(mtu) != true) extras?.onMtuChanged(DEFAULT_MTU)
    }

    override fun readFirmwareVersion() {
        val characteristic = firmwareCharacteristic ?: return
        enqueue(Operation(isWrite = false) { bluetoothGatt?.readCharacteristic(characteristic) == true })
    }

    private val state = MutableStateFlow(PairingState())
    override val pairingState: StateFlow<PairingState> = state

    override fun updatePairingState(state: PairingState) {
        this.state.value = state
    }

    override fun setListener(listener: BleTransportListener?) {
        this.listener = listener
    }

    override fun setExtrasListener(listener: AaruyTransportExtras?) {
        extras = listener
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private companion object {

        const val DEFAULT_MTU = 23
        val SERVICE_PUMP: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val CHARACTERISTIC_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val CHARACTERISTIC_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val SERVICE_DEVICE_INFO: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_FIRMWARE: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
