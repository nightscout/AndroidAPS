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
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.notifyAll
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.ble.GattAttributes.characteristicConfigDescriptor
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import app.aaps.pump.equil.manager.command.BaseCmd
import app.aaps.pump.equil.manager.command.CmdDevicesOldGet
import app.aaps.pump.equil.manager.command.CmdHistoryGet
import app.aaps.pump.equil.manager.command.CmdInsulinGet
import app.aaps.pump.equil.manager.command.CmdPair
import app.aaps.pump.equil.manager.command.CmdRunningModeGet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class EquilBLE @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    private val rxBus: RxBus
) {

    private var equilManager: EquilManager? = null
    private var mGattCallback: BluetoothGattCallback? = null
    private var notifyChara: BluetoothGattCharacteristic? = null
    private var writeChara: BluetoothGattCharacteristic? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    var isConnected = false
    var connecting = false
    var macAddress: String? = null
    private var bleHandler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    @Synchronized
    fun unBond(transmitterMAC: String?) {
        if (transmitterMAC == null) return
        try {
            val pairedDevices = bluetoothAdapter?.bondedDevices ?: return
            if (pairedDevices.isNotEmpty()) {
                for (device in pairedDevices) {
                    if (device.address == transmitterMAC) {
                        try {
                            val method = device.javaClass.getMethod("removeBond")
                            method.invoke(device)
                        } catch (e: Exception) {
                            aapsLogger.error(LTag.PUMPCOMM, "Error", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "Error", e)
        }
    }

    private fun bleConnectErrorForResult() {
        baseCmd?.let { baseCmd ->
            synchronized(baseCmd) {
                baseCmd.cmdSuccess = false
                baseCmd.notifyAll()
            }
        }
    }

    @Suppress("deprecation", "OVERRIDE_DEPRECATION")
    fun init(equilManager: EquilManager) {
        macAddress = equilManager.equilState?.address
        this.equilManager = equilManager
        aapsLogger.debug(LTag.PUMPBTCOMM, "initGatt======= ")
        mGattCallback = object : BluetoothGattCallback() {
            @Synchronized
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, i2: Int) {
                super.onConnectionStateChange(gatt, status, i2)
                val str = if (i2 == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
                val sb = "onConnectionStateChange called with status:$status, state:$str， i2: $i2， error133: "
                aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange $sb")
                connecting = false
                if (status == 133) {
                    unBond(macAddress)
                    SystemClock.sleep(50)
                    aapsLogger.debug(LTag.PUMPCOMM, "error133 ")
                    baseCmd?.resolvedResult = ResolvedResult.CONNECT_ERROR
                    bleConnectErrorForResult()
                    disconnect()
                    return
                }
                if (i2 == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    equilManager.equilState?.bluetoothConnectionState = BluetoothConnectionState.CONNECTED
                    handler.removeMessages(TIME_OUT_CONNECT_WHAT)
                    bluetoothGatt?.discoverServices()
                    updateCmdStatus(ResolvedResult.FAILURE)
                    //                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED));
                } else if (i2 == BluetoothProfile.STATE_DISCONNECTED) {
                    bleConnectErrorForResult()
                    disconnect()
                }
            }

            @Synchronized
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered received: $status")
                    return
                }
                val service = gatt.getService(UUID.fromString(GattAttributes.SERVICE_RADIO))
                if (service != null) {
                    notifyChara = service.getCharacteristic(UUID.fromString(GattAttributes.NRF_UART_NOTIFY))
                    writeChara = service.getCharacteristic(UUID.fromString(GattAttributes.NRF_UART_WRITE))
                    //                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED));
                    openNotification()
                    requestHighPriority()
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                try {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_WRITE_TIME_OUT)
                    writeData()
                } catch (e: Exception) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Error", e)
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                onCharacteristicChanged(gatt, characteristic)
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                requestHighPriority()
                decode(characteristic.value)
            }

            @Synchronized
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorWrite received: $status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorWrite: Wrote GATT Descriptor successfully.")
                    ready()
                }
            }
        }
    }

    @Suppress("deprecation")
    fun openNotification() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "openNotification: $isConnected")
        val r0 = bluetoothGatt?.setCharacteristicNotification(notifyChara, true)
        if (r0 == true) {
            val descriptor = notifyChara?.getDescriptor(characteristicConfigDescriptor)
            val v = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            descriptor?.setValue(v)
            val flag = bluetoothGatt?.writeDescriptor(descriptor)
            aapsLogger.debug(LTag.PUMPBTCOMM, "openNotification: $flag")
        }
    }

    @SuppressLint("MissingPermission")
    fun requestHighPriority() {
        bluetoothGatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    fun ready() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "ready: ===$baseCmd")
        dataList = ArrayList()
        baseCmd?.let { baseCmd ->
            equilResponse = baseCmd.getEquilResponse()
            indexData = 0
            writeData()
        }
    }

    private fun nextCmd2() {
        dataList = ArrayList()
        aapsLogger.debug(LTag.PUMPBTCOMM, "nextCmd===== ${baseCmd?.isEnd}====")
        baseCmd?.let { baseCmd ->
            equilResponse = baseCmd.getNextEquilResponse()
            aapsLogger.debug(LTag.PUMPBTCOMM, "nextCmd===== $baseCmd===${equilResponse?.send}")
            if ((equilResponse?.send?.size ?: 0) == 0) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "equilResponse is null")
                return
            }
            indexData = 0
            writeData()
        }
    }

    fun disconnect() {
        isConnected = false
        startTrue = false
        autoScan = false
        equilManager?.equilState?.bluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
        aapsLogger.debug(LTag.PUMPBTCOMM, "Closing GATT connection")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        baseCmd = null
        preCmd = null
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
    }

    fun closeBleAuto() {
        handler.postDelayed({
            disconnect()
        }, EquilConst.EQUIL_BLE_NEXT_CMD)
    }

    var autoScan = true
    private fun findEquil(mac: String) {
        if (mac.isEmpty()) return
        if (isConnected) return
        val equilDevice: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(mac)
        if (autoScan) startScan()
        else connectEquil(equilDevice)
    }

    fun connectEquil(device: BluetoothDevice?) {
        handler.postDelayed({
            if (device != null) {
                aapsLogger.debug(LTag.PUMPCOMM, "connectEquil======")
                bluetoothGatt = device.connectGatt(context, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }, 500)
    }

    private var baseCmd: BaseCmd? = null
    private var preCmd: BaseCmd? = null
    fun writeCmd(baseCmd: BaseCmd) {
        aapsLogger.debug(LTag.PUMPCOMM, "writeCmd {}", baseCmd)
        this.baseCmd = baseCmd
        val mac: String = when (baseCmd) {
            is CmdPair -> baseCmd.address
            is CmdDevicesOldGet -> baseCmd.address
            else -> equilManager?.equilState?.address ?: error("Unknown MAC address")
        }
        autoScan = baseCmd is CmdRunningModeGet || baseCmd is CmdInsulinGet
        if (isConnected && baseCmd.isPairStep()) {
            ready()
        } else if (isConnected) {
            preCmd?.let { preCmd ->
                baseCmd.runCode = preCmd.runCode
                baseCmd.runPwd = preCmd.runPwd
                nextCmd2()
            }
        } else {
            findEquil(mac)
            handler.sendEmptyMessageDelayed(TIME_OUT_CONNECT_WHAT, baseCmd.connectTimeOut.toLong())
        }
        preCmd = baseCmd
    }

    fun readHistory(baseCmd: CmdHistoryGet) {
        if (isConnected && preCmd != null) {
            baseCmd.runCode = preCmd!!.runCode
            baseCmd.runPwd = preCmd!!.runPwd
            this.baseCmd = baseCmd
            nextCmd2()
            preCmd = baseCmd
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "readHistory error")
        }
    }

    private var equilResponse: EquilResponse? = null
    private var indexData = 0
    fun writeData() {
        equilResponse?.let { equilResponse ->
            val diff = System.currentTimeMillis() - equilResponse.cmdCreateTime
            if (diff < EquilConst.EQUIL_CMD_TIME_OUT) {
                if (indexData < equilResponse.send.size) {
                    val data = equilResponse.send[indexData].array()
                    write(data)
                    indexData++
                } else { // no more data to send
                }
            } else aapsLogger.debug(LTag.PUMPCOMM, "equil cmd time out ")
        }
    }

    @Suppress("deprecation")
    private fun write(bytes: ByteArray) {
        if (writeChara == null || bluetoothGatt == null) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "write disconnect ")
            disconnect()
            return
        }
        writeChara?.setValue(bytes)
        aapsLogger.debug(LTag.PUMPBTCOMM, "write: ${Utils.bytesToHex(bytes)}")
        bluetoothGatt?.writeCharacteristic(writeChara)
    }

    private var dataList: List<String> = ArrayList()
    @Synchronized
    fun decode(buffer: ByteArray) {
        val str = Utils.bytesToHex(buffer)
        aapsLogger.debug(LTag.PUMPBTCOMM, "decode=====$str")
        val response = baseCmd?.decodeEquilPacket(buffer)
        if (response != null) {
            writeConf(response)
            dataList = ArrayList()
        }
    }

    private fun writeConf(equilResponse: EquilResponse?) {
        try {
            dataList = ArrayList()
            this.equilResponse = equilResponse
            indexData = 0
            writeData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var handler: Handler = object : Handler(HandlerThread(this::class.simpleName + "MessageHandler").also { it.start() }.looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                TIME_OUT_WHAT -> stopScan()

                TIME_OUT_CONNECT_WHAT -> {
                    stopScan()
                    aapsLogger.debug(LTag.PUMPCOMM, "TIME_OUT_CONNECT_WHAT====")
                    baseCmd?.resolvedResult = ResolvedResult.CONNECT_ERROR
                    bleConnectErrorForResult()
                    disconnect()
                }
            }
        }
    }
    private var startTrue = false
    private fun startScan() {
        macAddress = equilManager?.equilState?.address
        aapsLogger.debug(LTag.PUMPBTCOMM, "startScan====$startTrue====$macAddress===")
        if (macAddress.isNullOrEmpty()) return
        if (startTrue) return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            try {
                val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
                if (bluetoothLeScanner != null) {
                    updateCmdStatus(ResolvedResult.NOT_FOUNT)
                    connecting = true
                    bluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), scanCallback)
                }
            } catch (_: IllegalStateException) {
            } // ignore BT not on
        } else {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
        }
    }

    private fun updateCmdStatus(result: ResolvedResult) {
        baseCmd?.resolvedResult = result
    }

    fun connect(from: String) {
        aapsLogger.debug(LTag.PUMPCOMM, "connect====startTrue=$startTrue====isConnected=$isConnected from $from")
        if (startTrue || isConnected) {
            return
        }
        autoScan = true
        baseCmd = null
        startScan()
    }

    private fun buildScanFilters(): List<ScanFilter> {
        val scanFilterList = ArrayList<ScanFilter>()
        if (TextUtils.isEmpty(macAddress)) {
            return scanFilterList
        }
        val scanFilterBuilder = ScanFilter.Builder()
        scanFilterBuilder.setDeviceAddress(macAddress)
        scanFilterList.add(scanFilterBuilder.build())
        return scanFilterList
    }

    private fun buildScanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
        builder.setReportDelay(0)
        return builder.build()
    }

    private var scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val name: String? = result.device.name
            if (name?.isNotEmpty() == true) {
                try {
                    bleHandler.post {
                        equilManager?.decodeData(result.scanRecord!!.bytes, autoScan)
                    }
                    stopScan()
                    if (autoScan) {
                        updateCmdStatus(ResolvedResult.CONNECT_ERROR)
                        connectEquil(result.device)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopScan() {
        startTrue = false
        handler.removeMessages(TIME_OUT_WHAT)
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (isBluetoothAvailable) bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter?.isEnabled == true && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON

    companion object {

        const val TIME_OUT_WHAT = 0x12
        const val TIME_OUT_CONNECT_WHAT = 0x13
    }
}
