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
import app.aaps.pump.equil.manager.command.CmdPair
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
                    // Link is up: stop the parallel advert-harvest scan (the pump stops advertising once
                    // connected anyway). If it already caught an advert it stopped itself in onScanResult.
                    stopScan()
                    synchronized(notifyLock) {
                        // New link: notifications not yet enabled. Block command dispatch until onDescriptorWrite.
                        notificationEnabled = false
                        pendingCmd = null
                    }
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
                    synchronized(notifyLock) {
                        notificationEnabled = true
                        // Flush a command that writeCmd deferred while notifications were coming up.
                        if (pendingCmd != null) {
                            pendingCmd = null
                            ready()
                        }
                    }
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
        stopScan() // stop any in-flight advert-harvest scan (hybrid connect)
        isConnected = false
        connecting = false
        startTrue = false
        connectInitiated = false
        autoScan = false
        equilManager?.equilState?.bluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
        aapsLogger.debug(LTag.PUMPBTCOMM, "Closing GATT connection")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        baseCmd = null
        preCmd = null
        synchronized(notifyLock) {
            notificationEnabled = false
            pendingCmd = null
        }
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
    }

    fun closeBleAuto() {
        // Tear down immediately. The AAPS command queue owns the connection lifecycle: after the last
        // command it holds the link for waitForDisconnectionInSeconds() (5 s) for reuse and only then
        // calls Pump.disconnect() -> here. No extra driver-side linger is needed, and an immediate
        // teardown avoids the mid-command race that a deferred, cancellable timer would introduce.
        disconnect()
    }

    var autoScan = true
    private fun findEquil(mac: String) {
        if (mac.isEmpty()) return
        if (isConnected) return
        // Known pump: connect straight to the MAC (autoConnect), no scan. See connect() / #5040.
        // Mirror connect()'s state handling so isConnecting() reflects the in-flight direct connect.
        connecting = true
        equilManager?.equilState?.bluetoothConnectionState = BluetoothConnectionState.CONNECTING
        connectEquil(bluetoothAdapter?.getRemoteDevice(mac))
    }

    fun connectEquil(device: BluetoothDevice?) {
        handler.postDelayed({
            if (device != null) {
                aapsLogger.debug(LTag.PUMPCOMM, "connectEquil======")
                // autoConnect = true: the Android stack completes the link as soon as the (known/bonded)
                // pump is in range, with no app-level scan. This replaces flaky scan discovery, which took
                // 60-90 s on many phones and caused command timeouts / "no insulin delivered" (#5040).
                bluetoothGatt = device.connectGatt(context, true, mGattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }, 500)
    }

    private var baseCmd: BaseCmd? = null
    private var preCmd: BaseCmd? = null

    // Notification-readiness gate for the current GATT connection. Android allows only ONE outstanding
    // GATT operation at a time. When the queue's connect() phase opens the link, `isConnected` flips
    // true at onConnectionStateChange(CONNECTED) - BEFORE onServicesDiscovered runs openNotification()
    // (the notify-descriptor write). If a command's writeCmd then writes its first characteristic packet
    // in that window, it collides with the pending descriptor write: writeDescriptor() returns false
    // (log: "openNotification: false"), notifications never enable, the pump's replies never arrive, and
    // the command idle-times-out after ~9 s -> "Pump connection failure / manually check delivered
    // insulin" (bolus, tempBasal, and profile/CmdSettingSet all hit this via different writeCmd branches).
    // Fix: never send on a connected link until onDescriptorWrite confirms notifications are enabled;
    // hold the command in `pendingCmd` and let onDescriptorWrite flush it. See #4910 (and its follow-up).
    private val notifyLock = Any()
    @Volatile private var notificationEnabled = false
    private var pendingCmd: BaseCmd? = null

    fun writeCmd(baseCmd: BaseCmd) {
        aapsLogger.debug(LTag.PUMPCOMM, "writeCmd {}", baseCmd)
        this.baseCmd = baseCmd
        val mac: String = when (baseCmd) {
            is CmdPair          -> baseCmd.address
            is CmdDevicesOldGet -> baseCmd.address
            else                -> equilManager?.equilState?.address ?: error("Unknown MAC address")
        }
        if (isConnected) {
            synchronized(notifyLock) {
                if (!notificationEnabled) {
                    // Fresh link, notifications not enabled yet: defer ALL send paths (pair step,
                    // continuation, or first command) so the characteristic write does not collide with
                    // the openNotification() descriptor write. onDescriptorWrite flushes pendingCmd.
                    pendingCmd = baseCmd
                    preCmd = baseCmd
                    return
                }
            }
        }
        if (isConnected && baseCmd.isPairStep()) {
            ready()
        } else if (isConnected) {
            val prevCmd = preCmd
            if (prevCmd != null) {
                baseCmd.runCode = prevCmd.runCode
                baseCmd.runPwd = prevCmd.runPwd
                nextCmd2()
            } else {
                // GATT link opened by the queue's connect() phase, notifications already up: send this
                // command as the first one on the open link (else the pump idle-disconnects, status 19).
                ready()
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
            synchronized(baseCmd) { (baseCmd as Any).notifyAll() }
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
                TIME_OUT_WHAT         -> stopScan()

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

    // One-shot guard: set true when onScanResult fires the connect for the current scan session, re-armed at
    // each startScan(). Prevents the rapid LOW_LATENCY result stream from opening multiple GATT clients.
    private var connectInitiated = false

    private fun startScan() {
        macAddress = equilManager?.equilState?.address
        aapsLogger.debug(LTag.PUMPBTCOMM, "startScan====$startTrue====$macAddress===")
        if (macAddress.isNullOrEmpty()) return
        if (startTrue) return
        startTrue = true
        connectInitiated = false
        connecting = true
        equilManager?.equilState?.bluetoothConnectionState = BluetoothConnectionState.CONNECTING
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            try {
                val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
                if (bluetoothLeScanner != null) {
                    updateCmdStatus(ResolvedResult.NOT_FOUNT)
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
        aapsLogger.debug(LTag.PUMPCOMM, "connect====connecting=$connecting====isConnected=$isConnected from $from")
        if (connecting || isConnected) {
            return
        }
        baseCmd = null
        macAddress = equilManager?.equilState?.address
        val device = macAddress?.takeIf { it.isNotEmpty() }?.let { bluetoothAdapter?.getRemoteDevice(it) }
        if (device != null) {
            // Known/bonded pump: connect straight to its MAC (see connectEquil, autoConnect=true) instead
            // of scanning-to-connect. Scan-to-connect was the #5040 bottleneck (60-90 s on many phones).
            connecting = true
            equilManager?.equilState?.bluetoothConnectionState = BluetoothConnectionState.CONNECTING
            connectEquil(device)
            // Hybrid: run a best-effort advertisement harvest IN PARALLEL. It does NOT gate the connection
            // (autoConnect above owns that), but the advert carries data the GATT path can't get: the pump's
            // current history index (needed so loadEquilHistory reads new records), battery/reservoir, and the
            // live alarm state. autoScan=false so onScanResult only decodes the advert - it does not open a
            // second GATT client. The scan is stopped on CONNECTED / onScanResult / disconnect. See #5040.
            autoScan = false
            startScan()
        } else {
            autoScan = true
            startScan()
        }
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
        // Command connects are latency-sensitive (a bolus/temp-basal is waiting on discovery). The default
        // SCAN_MODE_LOW_POWER duty-cycles the radio and can take tens of seconds to surface a bonded pump on
        // some phones, long enough for the command to time out. Use LOW_LATENCY so the pump is found in ~1 s.
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        builder.setReportDelay(0)
        return builder.build()
    }

    private var scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // The scan is filtered by MAC address (buildScanFilters), so every result IS the target pump.
            // Do NOT gate on result.device.name: it is frequently null until the OS caches the device name,
            // which silently drops valid matches and stalls discovery for tens of seconds. Guard with a
            // one-shot flag so the rapid LOW_LATENCY result stream opens only a single GATT client.
            if (connectInitiated) return
            connectInitiated = true
            try {
                result.scanRecord?.bytes?.let { bytes ->
                    bleHandler.post {
                        equilManager?.decodeData(bytes, autoScan)
                    }
                }
                stopScan()
                if (autoScan) {
                    updateCmdStatus(ResolvedResult.CONNECT_ERROR)
                    connectEquil(result.device)
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "onScanResult error", e)
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
