package app.aaps.pump.danars.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.utils.extensions.safeEnable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleTransportImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val bluetoothAdapter: BluetoothAdapter?
) : BleTransport {

    companion object {

        private const val WRITE_DELAY_MILLIS: Long = 50
        private const val UART_READ_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        private const val UART_WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
        private const val UART_BLE5_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    private var listener: BleTransportListener? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var uartRead: BluetoothGattCharacteristic? = null
    private var uartWrite: BluetoothGattCharacteristic? = null

    private val uartReadBTGattChar: BluetoothGattCharacteristic
        get() = uartRead
            ?: BluetoothGattCharacteristic(
                UUID.fromString(UART_READ_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0
            ).also { uartRead = it }

    private val uartWriteBTGattChar: BluetoothGattCharacteristic
        get() = uartWrite
            ?: BluetoothGattCharacteristic(
                UUID.fromString(UART_WRITE_UUID),
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0
            ).also { uartWrite = it }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            listener?.onConnectionStateChanged(newState == BluetoothProfile.STATE_CONNECTED)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            listener?.onServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            listener?.onCharacteristicChanged(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            listener?.onCharacteristicChanged(characteristic.value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            listener?.onCharacteristicWritten()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            listener?.onDescriptorWritten()
        }
    }

    // --- BleTransport ---

    override val adapter: BleAdapter = AdapterImpl()
    override val scanner: BleScanner = ScannerImpl()
    override val gatt: BleGatt = GattImpl()

    private val _pairingState = MutableStateFlow(PairingState())
    override val pairingState: StateFlow<PairingState> = _pairingState

    override fun updatePairingState(state: PairingState) {
        _pairingState.value = state
    }

    override fun setListener(listener: BleTransportListener?) {
        this.listener = listener
    }

    // --- BleAdapter ---

    private inner class AdapterImpl : BleAdapter {

        @SuppressLint("MissingPermission")
        override fun enable() {
            bluetoothAdapter?.safeEnable()
        }

        @SuppressLint("MissingPermission")
        override fun getDeviceName(address: String): String? {
            if (!hasPermission()) return null
            return bluetoothAdapter?.getRemoteDevice(address)?.name
        }

        @SuppressLint("MissingPermission")
        override fun isDeviceBonded(address: String): Boolean {
            if (!hasPermission()) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            return device.bondState != android.bluetooth.BluetoothDevice.BOND_NONE
        }

        @SuppressLint("MissingPermission")
        override fun createBond(address: String): Boolean {
            if (!hasPermission()) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            return device.createBond()
        }

        @SuppressLint("MissingPermission")
        override fun removeBond(address: String) {
            if (!hasPermission()) return
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
            try {
                device.javaClass.getMethod("removeBond").invoke(device)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Removing bond has been failed. ${e.message}")
            }
        }
    }

    // --- BleScanner ---

    private inner class ScannerImpl : BleScanner {

        private var scanCallback: ScanCallback? = null
        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 50)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        @SuppressLint("MissingPermission")
        override fun startScan() {
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val name = device.name ?: return
                    if (name.isNotEmpty()) {
                        _scannedDevices.tryEmit(ScannedDevice(name = name, address = device.address))
                    }
                }
            }
            try {
                bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
            } catch (_: IllegalStateException) {
                // BT not on
            }
        }

        @SuppressLint("MissingPermission")
        override fun stopScan() {
            try {
                scanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
            } catch (_: IllegalStateException) {
                // BT not on
            }
            scanCallback = null
        }
    }

    // --- BleGatt ---

    private inner class GattImpl : BleGatt {

        @SuppressLint("MissingPermission")
        override fun connect(address: String): Boolean {
            if (!hasPermission()) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            // Close any existing connection
            bluetoothGatt?.let {
                try {
                    it.disconnect()
                    SystemClock.sleep(200)
                    it.close()
                } catch (e: Exception) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Error closing existing connection: ${e.message}")
                }
            }
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            return bluetoothGatt != null
        }

        @SuppressLint("MissingPermission")
        override fun disconnect() {
            if (!hasPermission()) return
            bluetoothGatt?.disconnect()
        }

        @SuppressLint("MissingPermission")
        override fun close() {
            bluetoothGatt?.close()
            bluetoothGatt = null
        }

        @SuppressLint("MissingPermission")
        override fun discoverServices() {
            bluetoothGatt?.discoverServices()
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun findCharacteristics(): Boolean {
            val gattServices: List<BluetoothGattService> = bluetoothGatt?.services ?: return false
            for (gattService in gattServices) {
                for (gattCharacteristic in gattService.characteristics) {
                    val uuid = gattCharacteristic.uuid.toString()
                    if (UART_READ_UUID == uuid) {
                        uartRead = gattCharacteristic
                    }
                    if (UART_WRITE_UUID == uuid) {
                        uartWrite = gattCharacteristic
                    }
                }
            }
            return uartRead != null && uartWrite != null
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun enableNotifications() {
            val characteristic = uartRead ?: uartReadBTGattChar
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            // Dana-i BLE5 specific descriptor
            characteristic.getDescriptor(UUID.fromString(UART_BLE5_UUID))?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt?.writeDescriptor(it)
            }
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun writeCharacteristic(data: ByteArray) {
            Thread(Runnable {
                SystemClock.sleep(WRITE_DELAY_MILLIS)
                if (bluetoothGatt == null) return@Runnable
                val characteristic = uartWrite ?: uartWriteBTGattChar
                characteristic.value = data
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                bluetoothGatt?.writeCharacteristic(characteristic)
            }).start()
            SystemClock.sleep(50)
        }
    }

    private fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
