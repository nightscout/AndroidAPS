package app.aaps.pump.carelevo.ble

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
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
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
import app.aaps.pump.carelevo.config.BleEnvConfig
import app.aaps.pump.carelevo.ext.convertBytesToHex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [CarelevoBleTransport] over Android's `BluetoothGatt`.
 *
 * Structurally a clone of `EquilBleTransportImpl` — carelevo is the same single-service /
 * single-write-characteristic / single-notify-characteristic patch-pump shape, so the fleet
 * template maps 1:1. The three sub-interfaces are private inner classes sharing the impl's
 * mutable GATT state, and the single [BluetoothGattCallback] fans every event out to one
 * [BleTransportListener] (the [app.aaps.pump.carelevo.ble.gatt.BleTransportGattConnection]
 * adapter, which bridges into the [BleClient] correlation layer).
 *
 * UUIDs come from [BleEnvConfig]: service `e1b40001`, write char (rx) `e1b40002`,
 * notify char (tx) `e1b40003`, CCCD `2902`. Notifications (not indications), no MTU negotiation.
 */
@SuppressLint("MissingPermission")
@Singleton
class CarelevoBleTransportImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : CarelevoBleTransport {

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
            val service = gatt.getService(SERVICE_UUID)
            if (service != null) {
                notifyChara = service.getCharacteristic(TX_CHAR_UUID)
                writeChara = service.getCharacteristic(RX_CHAR_UUID)
            }
            listener?.onServicesDiscovered(service != null && notifyChara != null && writeChara != null)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            listener?.onCharacteristicWritten()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            listener?.onCharacteristicChanged(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            // Log every inbound frame (opcode = first byte) so patch-pushed notifications — alarms and the
            // auto-resume report — are visible even though the unsolicited path is not yet consumed. The
            // pre-migration driver logged incoming frames here; the transport migration dropped it.
            aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicChanged incoming: ${data.convertBytesToHex()}")
            listener?.onCharacteristicChanged(data)
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

    override val adapter: BleAdapter = CarelevoAdapterImpl()
    override val scanner: BleScanner = CarelevoScannerImpl()
    override val gatt: BleGatt = CarelevoGattImpl()

    private val _pairingState = MutableStateFlow(PairingState())
    override val pairingState: StateFlow<PairingState> = _pairingState

    override fun updatePairingState(state: PairingState) {
        _pairingState.value = state
    }

    override fun setListener(listener: BleTransportListener?) {
        val existing = this.listener
        if (existing != null && listener != null && existing !== listener) {
            // This @Singleton holds a single listener slot; a new adapter registering before the
            // previous one closed would silently orphan it (its in-flight ops would hang). This is a
            // defensive guard against that overlap.
            aapsLogger.warn(LTag.PUMPBTCOMM, "setListener overwriting an active listener; previous connection not closed?")
        }
        this.listener = listener
    }

    // --- BleAdapter ---

    private inner class CarelevoAdapterImpl : BleAdapter {

        override fun enable() {
            // not used by CareLevo
        }

        override fun getDeviceName(address: String): String? {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return null
            return bluetoothAdapter?.getRemoteDevice(address)?.name
        }

        override fun isDeviceBonded(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            return device.bondState == BluetoothDevice.BOND_BONDED
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

    private inner class CarelevoScannerImpl : BleScanner {

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
                                scanRecordBytes = result.scanRecord?.bytes,
                                rssi = result.rssi
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
                            .setServiceUuid(ParcelUuid(SERVICE_UUID))
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

    private inner class CarelevoGattImpl : BleGatt {

        override fun connect(address: String): Boolean {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            // Release any previous client before opening a new one. Android caps concurrent GATT
            // client interfaces; leaking them (connectGatt without close) leads to status 133 and
            // an eventual inability to connect at all.
            bluetoothGatt?.close()
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
            val gatt = bluetoothGatt ?: return
            val result = gatt.setCharacteristicNotification(chara, true)
            if (result) {
                val descriptor = chara.getDescriptor(CCCD_UUID) ?: return
                val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
                if (!writeStarted) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "enableNotifications: writeDescriptor could not start")
                }
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
            // setValue-then-write is NOT atomic on the shared characteristic object; it is safe only
            // because BleTransportGattConnection.gattMutex serializes all suspend GATT ops. Do not
            // decouple this transport from that serialization without adding one here.
            val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(chara, data, chara.writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                chara.value = data
                gatt.writeCharacteristic(chara)
            }
            if (!writeStarted) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "writeCharacteristic: writeCharacteristic could not start")
            }
        }

        override fun requestConnectionPriority(priority: Int) {
            bluetoothGatt?.requestConnectionPriority(priority)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {

        private val SERVICE_UUID: UUID = UUID.fromString(BleEnvConfig.BLE_SERVICE_UUID)
        private val TX_CHAR_UUID: UUID = UUID.fromString(BleEnvConfig.BLE_TX_CHAR_UUID)
        private val RX_CHAR_UUID: UUID = UUID.fromString(BleEnvConfig.BLE_RX_CHAR_UUID)
        private val CCCD_UUID: UUID = UUID.fromString(BleEnvConfig.BLE_CCC_DESCRIPTOR)
    }
}
