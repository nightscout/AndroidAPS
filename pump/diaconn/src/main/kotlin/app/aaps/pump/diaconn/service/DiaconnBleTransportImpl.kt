package app.aaps.pump.diaconn.service

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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.utils.extensions.connectGattCompat
import app.aaps.core.utils.extensions.safeEnable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * The real Bluetooth link to a Diaconn G8: a Nordic UART service, answers as notifications, requests written without
 * response. The in-tree emulator replaces it when the `emulate_diaconn` option is on (`DiaconnTransportBindings`).
 *
 * It does what `BLECommonService` did with Android Bluetooth itself before, with the same waits.
 */
@SingleIn(AppScope::class)
@Inject
class DiaconnBleTransportImpl(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : BleTransport {

    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private var listener: BleTransportListener? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var uartIndicate: BluetoothGattCharacteristic? = null
    private var uartWrite: BluetoothGattCharacteristic? = null

    private val uartWriteBTGattChar: BluetoothGattCharacteristic
        get() = uartWrite
            ?: BluetoothGattCharacteristic(
                UUID.fromString(WRITE_UUID),
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0
            ).also { uartWrite = it }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            listener?.onConnectionStateChanged(newState == BluetoothProfile.STATE_CONNECTED)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            listener?.onServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            listener?.onCharacteristicChanged(characteristic.value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            listener?.onCharacteristicWritten()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            listener?.onDescriptorWritten()
        }
    }

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
        override fun isDeviceBonded(address: String): Boolean = bondState(address) != BondState.NONE

        @SuppressLint("MissingPermission")
        override fun bondState(address: String): BondState {
            if (!hasPermission()) return BondState.NONE
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return BondState.NONE
            return when (device.bondState) {
                BluetoothDevice.BOND_BONDED  -> BondState.BONDED
                BluetoothDevice.BOND_BONDING -> BondState.BONDING
                else                         -> BondState.NONE
            }
        }

        @SuppressLint("MissingPermission")
        override fun createBond(address: String): Boolean {
            if (!hasPermission()) return false
            return bluetoothAdapter?.getRemoteDevice(address)?.createBond() == true
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

    /** Finds only Diaconn pumps: they advertise the Nordic UART service. */
    private inner class ScannerImpl : BleScanner {

        private var scanCallback: ScanCallback? = null
        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 50)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        @SuppressLint("MissingPermission")
        override fun startScan() {
            // Without the permission Android throws a SecurityException, and the app ends
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                aapsLogger.error(LTag.PUMPBTCOMM, "BLUETOOTH_SCAN permission missing, no scan")
                return
            }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val name = device.name ?: return
                    if (name.isNotEmpty()) _scannedDevices.tryEmit(ScannedDevice(name = name, address = device.address))
                }
            }
            scanCallback = callback
            try {
                val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID))).build()
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                bluetoothAdapter?.bluetoothLeScanner?.startScan(listOf(filter), settings, callback)
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

    private inner class GattImpl : BleGatt {

        @SuppressLint("MissingPermission")
        override fun connect(address: String): Boolean {
            if (!hasPermission()) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            // Close any existing connection
            bluetoothGatt?.let {
                it.disconnect()
                SystemClock.sleep(200)
                it.close()
                SystemClock.sleep(200)
                bluetoothGatt = null
            }
            bluetoothGatt = device.connectGattCompat(context, false, gattCallback)
            return bluetoothGatt != null
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun disconnect() {
            if (!hasPermission()) return
            uartIndicate?.let { bluetoothGatt?.setCharacteristicNotification(it, false) }
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

        override fun findCharacteristics(): Boolean {
            val gattServices = bluetoothGatt?.services ?: return false
            for (gattService in gattServices) {
                for (gattCharacteristic in gattService.characteristics) {
                    when (gattCharacteristic.uuid.toString()) {
                        INDICATION_UUID -> uartIndicate = gattCharacteristic
                        WRITE_UUID      -> uartWrite = gattCharacteristic
                    }
                }
            }
            return uartIndicate != null && uartWrite != null
        }

        /** Notifications on, and the descriptor written as nRF Connect does it. */
        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun enableNotifications() {
            val characteristic = uartIndicate ?: return
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG_UUID))?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt?.writeDescriptor(it)
            }
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun writeCharacteristic(data: ByteArray) {
            Thread(Runnable {
                SystemClock.sleep(WRITE_DELAY_MILLIS)
                val gatt = bluetoothGatt
                if (gatt == null) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "No link, the request is not written")
                    return@Runnable
                }
                val characteristic = uartWrite ?: uartWriteBTGattChar
                characteristic.value = data
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(characteristic)
            }).start()
            SystemClock.sleep(WRITE_DELAY_MILLIS)
        }
    }

    private fun hasPermission(permission: String = Manifest.permission.BLUETOOTH_CONNECT): Boolean =
        ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {

        const val WRITE_DELAY_MILLIS: Long = 50
        const val SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        const val INDICATION_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
        const val WRITE_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        const val CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }
}
