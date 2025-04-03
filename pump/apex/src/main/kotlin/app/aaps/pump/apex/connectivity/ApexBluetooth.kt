package app.aaps.pump.apex.connectivity

import app.aaps.pump.apex.interfaces.ApexBluetoothCallback
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.keys.Preferences
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.toHex
import app.aaps.pump.apex.R
import app.aaps.pump.apex.connectivity.commands.device.DeviceCommand
import app.aaps.pump.apex.connectivity.commands.pump.PumpCommand
import app.aaps.pump.apex.utils.keys.ApexStringKey
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import javax.inject.Inject

class ApexBluetooth @Inject constructor(
    val aapsLogger: AAPSLogger,
    val preferences: Preferences,
    val context: Context,
    val rxBus: RxBus,
) : ScanCallback() {
    companion object {
        private val READ_SERVICE = ParcelUuid.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        private val WRITE_SERVICE = ParcelUuid.fromString("0000FFE5-0000-1000-8000-00805F9B34FB")

        private val READ_UUID = UUID.fromString("0000FFE4-0000-1000-8000-00805F9B34FB")
        private val WRITE_UUID = UUID.fromString("0000FFE9-0000-1000-8000-00805F9B34FB")
        private val CCC_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val WRITE_DELAY_MS = 250
    }

    private val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
    private var callback: ApexBluetoothCallback? = null

    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null

    private var mtu: Int = 512

    private val readMutex = Mutex()
    private var lastCommand: PumpCommand? = null
    private var _status: Status = Status.DISCONNECTED

    val status: Status
        get() = _status

    fun setCallback(callback: ApexBluetoothCallback) {
        this.callback = callback
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    @Synchronized
    fun send(command: DeviceCommand) {
        if (checkBT()) return
        if (status != Status.CONNECTED) return

        Thread {
            SystemClock.sleep(WRITE_DELAY_MS.toLong())
            val data = command.serialize()
            aapsLogger.debug(LTag.PUMPBTCOMM, "DEVICE -> ${data.toHex()}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt!!.writeCharacteristic(
                    writeCharacteristic!!,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                writeCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                writeCharacteristic!!.setValue(data)
                bluetoothGatt!!.writeCharacteristic(writeCharacteristic!!)
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connect")
        if (preferences.get(ApexStringKey.SerialNumber).isEmpty()) return
        if (checkBT()) return
        _status = Status.CONNECTING
        if (preferences.get(ApexStringKey.BluetoothAddress).isNotEmpty()) return reconnect()

        aapsLogger.debug(LTag.PUMPBTCOMM, "Scan started")
        bluetoothAdapter.bluetoothLeScanner.startScan(
            listOf(
                ScanFilter.Builder()
                    .setDeviceName("APEX${preferences.get(ApexStringKey.SerialNumber)}")
                    .build(),
                ScanFilter.Builder()
                    .setServiceUuid(READ_SERVICE)
                    .build(),
                ScanFilter.Builder()
                    .setServiceUuid(WRITE_SERVICE)
                    .build(),
            ),
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(), this
        )
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun disconnect() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnect")
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        if (checkBT()) return
        when (status) {
            Status.CONNECTED -> {
                bluetoothGatt?.disconnect()
            }
            Status.CONNECTING -> {
                stopScan()
                SystemClock.sleep(100)
                bluetoothGatt?.close()
                SystemClock.sleep(100)
                bluetoothGatt = null
            }
            else -> return
        }
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
    }

    private fun checkBT(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
            aapsLogger.error(LTag.PUMPBTCOMM, "No Bluetooth permission!")
            return true
        }

        if (bluetoothAdapter == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "No Bluetooth adapter!")
            return true
        }
        return false
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    private fun setupGatt() {
        bluetoothGatt = bluetoothDevice!!.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                when (newState) {
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                        _status = Status.DISCONNECTED
                        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnected")
                        Thread { callback?.onDisconnect() }.start()
                        bluetoothGatt?.close()
                    }
                    BluetoothGatt.STATE_CONNECTED -> {
                        bluetoothGatt?.discoverServices()
                        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting | Discovering services")
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                this@ApexBluetooth.mtu = mtu
                aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting | Got MTU $mtu")
            }

            @Suppress("DEPRECATION")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return

                Thread {
                    gatt.requestMtu(512)
                    SystemClock.sleep(150)

                    aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting | Requesting notification")

                    writeCharacteristic = gatt.getService(WRITE_SERVICE.uuid).getCharacteristic(WRITE_UUID)
                    readCharacteristic = gatt.getService(READ_SERVICE.uuid).getCharacteristic(READ_UUID)
                    gatt.setCharacteristicNotification(readCharacteristic, true)

                    val ccc = readCharacteristic!!.getDescriptor(CCC_UUID)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(ccc)
                    }
                }.start()
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
                super.onDescriptorWrite(gatt, descriptor, status)
                Thread {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Connected | Notification status: $status")
                    gatt?.setCharacteristicNotification(readCharacteristic, true)
                    SystemClock.sleep(10)
                    _status = Status.CONNECTED
                    callback?.onConnect()
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Connected")
                }.start()
            }

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                onPumpData(characteristic, characteristic.value)
            }

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
                onPumpData(characteristic, characteristic.value)
            }
        }, BluetoothDevice.TRANSPORT_LE)

        if (bluetoothGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Connecting | Failed to set up GATT")
            _status = Status.DISCONNECTED
        }
    }

    private fun onPumpData(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "PUMP <- ${value.toHex()}")
        when (characteristic.uuid) {
            READ_UUID -> synchronized(readMutex) {
                // Update command or create new one
                if (lastCommand?.isCompleteCommand() == false)
                    lastCommand!!.update(value)
                else if (value.size > PumpCommand.MIN_SIZE)
                    lastCommand = PumpCommand(value)
                else
                    aapsLogger.error(LTag.PUMPBTCOMM, "Got invalid command of length ${value.size}")

                while (lastCommand != null && lastCommand!!.isCompleteCommand()) {
                    if (!lastCommand!!.verify()) {
                        aapsLogger.error(LTag.PUMPBTCOMM, "[${lastCommand!!.id?.name}] Command checksum is invalid! Expected ${lastCommand!!.checksum.toHex()}")
                        return
                    }

                    callback?.onPumpCommand(lastCommand!!)
                    lastCommand = lastCommand!!.trailing
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun reconnect() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting | Setting up GATT")
        bluetoothDevice = bluetoothAdapter!!.getRemoteDevice(preferences.get(ApexStringKey.BluetoothAddress))
        setupGatt()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun stopScan() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Scan stopped")
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        if (result == null) {
            _status = Status.DISCONNECTED
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Found device ${result.device.name}")
        stopScan()
        preferences.put(ApexStringKey.BluetoothAddress, result.device.address)
        reconnect()
    }

    enum class Status {
        DISCONNECTED,
        CONNECTING,
        CONNECTED;

        fun toLocalString(rh: ResourceHelper): String = when (this) {
            DISCONNECTED -> rh.gs(R.string.overview_connection_status_disconnected)
            CONNECTING -> rh.gs(R.string.overview_connection_status_connecting)
            CONNECTED -> rh.gs(R.string.overview_connection_status_connected)
        }
    }
}