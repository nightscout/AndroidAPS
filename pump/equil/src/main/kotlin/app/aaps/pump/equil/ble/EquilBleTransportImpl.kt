package app.aaps.pump.equil.ble

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class EquilBleTransportImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : EquilBleTransport {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private var listener: BleTransportListener? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyChara: BluetoothGattCharacteristic? = null
    private var writeChara: BluetoothGattCharacteristic? = null

    override var scanAddress: String? = null
    override var onGattError133: (() -> Unit)? = null

    @Suppress("deprecation", "OVERRIDE_DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        @Synchronized
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == 133) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "GATT error 133, removing bond")
                scanAddress?.let { adapter.removeBond(it) }
                SystemClock.sleep(50) // Give BT stack time to process bond removal
                onGattError133?.invoke()
                listener?.onConnectionStateChanged(false)
                return
            }
            listener?.onConnectionStateChanged(newState == BluetoothProfile.STATE_CONNECTED)
        }

        @Synchronized
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered received: $status")
                listener?.onServicesDiscovered(false)
                return
            }
            val service = gatt.getService(UUID.fromString(GattAttributes.SERVICE_RADIO))
            if (service != null) {
                notifyChara = service.getCharacteristic(UUID.fromString(GattAttributes.NRF_UART_NOTIFY))
                writeChara = service.getCharacteristic(UUID.fromString(GattAttributes.NRF_UART_WRITE))
            }
            listener?.onServicesDiscovered(service != null)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            listener?.onCharacteristicWritten()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            listener?.onCharacteristicChanged(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            listener?.onCharacteristicChanged(characteristic.value)
        }

        @Synchronized
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorWrite received: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener?.onDescriptorWritten()
            }
        }
    }

    // --- BleTransport ---

    override val adapter: BleAdapter = EquilAdapterImpl()
    override val scanner: BleScanner = EquilScannerImpl()
    override val gatt: BleGatt = EquilGattImpl()

    private val _pairingState = MutableStateFlow(PairingState())
    override val pairingState: StateFlow<PairingState> = _pairingState

    override fun updatePairingState(state: PairingState) {
        _pairingState.value = state
    }

    override fun setListener(listener: BleTransportListener?) {
        this.listener = listener
    }

    // --- BleAdapter ---

    private inner class EquilAdapterImpl : BleAdapter {

        override fun enable() {
            // not used by Equil
        }

        override fun getDeviceName(address: String): String? {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return null
            return bluetoothAdapter?.getRemoteDevice(address)?.name
        }

        override fun isDeviceBonded(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            return device.bondState != BluetoothDevice.BOND_NONE
        }

        override fun createBond(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            return device.createBond()
        }

        override fun removeBond(address: String) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            try {
                val pairedDevices = bluetoothAdapter?.bondedDevices ?: return
                for (device in pairedDevices) {
                    if (device.address == address) {
                        val method = device.javaClass.getMethod("removeBond")
                        method.invoke(device)
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPCOMM, "Error removing bond", e)
            }
        }
    }

    // --- BleScanner ---

    private inner class EquilScannerImpl : BleScanner {

        private var scanCallback: ScanCallback? = null
        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 10)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        override fun startScan() {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.device?.name
                    if (name?.isNotEmpty() == true) {
                        _scannedDevices.tryEmit(
                            ScannedDevice(
                                name = name,
                                address = result.device.address,
                                scanRecordBytes = result.scanRecord?.bytes
                            )
                        )
                    }
                }
            }

            try {
                val mac = scanAddress
                val filters = if (mac.isNullOrEmpty()) {
                    // Discovery scan: filter by service UUID (pairing wizard)
                    listOf(
                        ScanFilter.Builder()
                            .setServiceUuid(android.os.ParcelUuid.fromString(GattAttributes.SERVICE_RADIO))
                            .build()
                    )
                } else {
                    // Reconnection scan: filter by MAC address
                    listOf(
                        ScanFilter.Builder()
                            .setDeviceAddress(mac)
                            .build()
                    )
                }
                val settings = ScanSettings.Builder()
                    .setReportDelay(0)
                    .build()
                bluetoothAdapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            } catch (_: IllegalStateException) {
                // BT not on
            }
        }

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

    private inner class EquilGattImpl : BleGatt {

        override fun connect(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            return bluetoothGatt != null
        }

        override fun disconnect() {
            bluetoothGatt?.disconnect()
        }

        override fun close() {
            bluetoothGatt?.close()
            bluetoothGatt = null
            notifyChara = null
            writeChara = null
        }

        override fun discoverServices() {
            bluetoothGatt?.discoverServices()
        }

        override fun findCharacteristics(): Boolean {
            return notifyChara != null && writeChara != null
        }

        @Suppress("deprecation")
        override fun enableNotifications() {
            val chara = notifyChara ?: return
            val result = bluetoothGatt?.setCharacteristicNotification(chara, true)
            if (result == true) {
                val descriptor = chara.getDescriptor(GattAttributes.characteristicConfigDescriptor)
                descriptor?.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                bluetoothGatt?.writeDescriptor(descriptor)
            }
        }

        @Suppress("deprecation")
        override fun writeCharacteristic(data: ByteArray) {
            val chara = writeChara
            val gatt = bluetoothGatt
            if (chara == null || gatt == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "writeCharacteristic: not connected")
                listener?.onConnectionStateChanged(false)
                return
            }
            chara.setValue(data)
            gatt.writeCharacteristic(chara)
        }

        override fun requestConnectionPriority(priority: Int) {
            bluetoothGatt?.requestConnectionPriority(priority)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
