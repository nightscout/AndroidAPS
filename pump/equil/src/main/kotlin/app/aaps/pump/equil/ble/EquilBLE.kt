package app.aaps.pump.equil.ble

import android.bluetooth.BluetoothGatt
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.utils.notifyAll
import app.aaps.pump.equil.EquilConst
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EquilBLE @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val bleTransport: EquilBleTransport,
    private val rxBus: RxBus
) : BleTransportListener {

    private var equilManager: EquilManager? = null
    var isConnected = false
    var connecting = false
    var macAddress: String? = null
    private var bleHandler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    fun init(equilManager: EquilManager) {
        macAddress = equilManager.equilState?.address
        this.equilManager = equilManager
        aapsLogger.debug(LTag.PUMPBTCOMM, "initGatt======= ")
        bleTransport.setListener(this)
        bleTransport.onGattError133 = {
            aapsLogger.debug(LTag.PUMPCOMM, "error133 ")
            baseCmd?.resolvedResult = ResolvedResult.CONNECT_ERROR
        }
    }

    // --- BleTransportListener ---

    override fun onConnectionStateChanged(connected: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChanged connected=$connected")
        connecting = false
        if (connected) {
            isConnected = true
            equilManager?.equilState?.bluetoothConnectionState = BluetoothConnectionState.CONNECTED
            handler.removeMessages(TIME_OUT_CONNECT_WHAT)
            bleTransport.gatt.discoverServices()
            updateCmdStatus(ResolvedResult.FAILURE)
        } else {
            // For error 133, resolvedResult is already set to CONNECT_ERROR via onGattError133 callback
            bleConnectErrorForResult()
            disconnect()
        }
    }

    override fun onServicesDiscovered(success: Boolean) {
        if (!success) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered failed")
            return
        }
        bleTransport.gatt.enableNotifications()
        bleTransport.gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    override fun onDescriptorWritten() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorWritten: Wrote GATT Descriptor successfully.")
        ready()
    }

    override fun onCharacteristicChanged(data: ByteArray) {
        bleTransport.gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        decode(data)
    }

    override fun onCharacteristicWritten() {
        try {
            SystemClock.sleep(EquilConst.EQUIL_BLE_WRITE_TIME_OUT)
            writeData()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error", e)
        }
    }

    // --- Command sequencing ---

    private fun bleConnectErrorForResult() {
        baseCmd?.let { baseCmd ->
            synchronized(baseCmd) {
                baseCmd.cmdSuccess = false
                baseCmd.notifyAll()
            }
        }
    }

    @Synchronized fun ready() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "ready: ===$baseCmd")
        dataList = ArrayList()
        baseCmd?.let { baseCmd ->
            equilResponse = baseCmd.getEquilResponse()
            indexData = 0
            writeData()
        }
    }

    @Synchronized private fun nextCmd2() {
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
        bleTransport.gatt.disconnect()
        bleTransport.gatt.close()
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
        if (autoScan) startScan()
        else connectEquil(mac)
    }

    private fun connectEquil(address: String) {
        handler.postDelayed({
            aapsLogger.debug(LTag.PUMPCOMM, "connectEquil======")
            bleTransport.gatt.connect(address)
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
    @Synchronized fun writeData() {
        equilResponse?.let { equilResponse ->
            val diff = System.currentTimeMillis() - equilResponse.cmdCreateTime
            if (diff < EquilConst.EQUIL_CMD_TIME_OUT) {
                if (indexData < equilResponse.send.size) {
                    val data = equilResponse.send[indexData].array()
                    aapsLogger.debug(LTag.PUMPBTCOMM, "write: ${Utils.bytesToHex(data)}")
                    bleTransport.gatt.writeCharacteristic(data)
                    indexData++
                } else { // no more data to send
                }
            } else aapsLogger.debug(LTag.PUMPCOMM, "equil cmd time out ")
        }
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

    @Synchronized private fun writeConf(equilResponse: EquilResponse?) {
        try {
            dataList = ArrayList()
            this.equilResponse = equilResponse
            indexData = 0
            writeData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Scanning ---

    private var startTrue = false
    private fun startScan() {
        macAddress = equilManager?.equilState?.address
        aapsLogger.debug(LTag.PUMPBTCOMM, "startScan====$startTrue====$macAddress===")
        if (macAddress.isNullOrEmpty()) return
        if (startTrue) return

        bleTransport.scanAddress = macAddress
        updateCmdStatus(ResolvedResult.NOT_FOUNT)
        connecting = true

        scanJob = scope.launch {
            bleTransport.scanner.scannedDevices.collect { device ->
                device.scanRecordBytes?.let { bytes ->
                    bleHandler.post {
                        equilManager?.decodeData(bytes, autoScan)
                    }
                }
                stopScan()
                if (autoScan) {
                    updateCmdStatus(ResolvedResult.CONNECT_ERROR)
                    connectEquil(device.address)
                }
            }
        }

        bleTransport.scanner.startScan()
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

    fun stopScan() {
        startTrue = false
        handler.removeMessages(TIME_OUT_WHAT)
        scanJob?.cancel()
        scanJob = null
        bleTransport.scanner.stopScan()
    }

    @Synchronized
    fun unBond(transmitterMAC: String?) {
        if (transmitterMAC == null) return
        bleTransport.adapter.removeBond(transmitterMAC)
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

    companion object {

        const val TIME_OUT_WHAT = 0x12
        const val TIME_OUT_CONNECT_WHAT = 0x13
    }
}
