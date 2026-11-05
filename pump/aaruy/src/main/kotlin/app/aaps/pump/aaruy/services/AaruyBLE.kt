package app.aaps.pump.aaruy.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.comm.AaruyBleClient
import app.aaps.pump.aaruy.comm.AaruyBleTransport
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.comm.DailyInfo
import app.aaps.pump.aaruy.comm.LogInfo
import app.aaps.pump.aaruy.comm.LogItem
import app.aaps.pump.aaruy.comm.StatusInfo
import app.aaps.pump.aaruy.comm.BleStatus
import app.aaps.pump.aaruy.comm.AaruyBleListener
import app.aaps.pump.aaruy.comm.ScanResultListener
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AaruyType
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.common.util.AaruySetting
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfo
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.events.EventAaruyDeviceChange
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.support.v18.scanner.ScanResult
import dev.zacsweers.metro.AppScope
import kotlin.math.abs
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import app.aaps.pump.aaruy.keys.AaruyStringKey

@SingleIn(AppScope::class)
@Inject
class AaruyBLE(
    transport: AaruyBleTransport,
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    private val aaruyPump: AaruyPump,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val historyUnitInfoDao: HistoryUnitInfoDao,
    private val historyDailyUnitInfoDao: HistoryDailyUnitInfoDao,
    private val detailedBolusInfoStorage: DetailedBolusInfoStorage,
    private val pumpSync: PumpSync,
    private val tempBasalSync: AaruyTempBasalSync,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val BOLUS_START_TIMEOUT_MS = 5000L
    private val BOLUS_STOP_TIMEOUT_MS = 500L
    private val COMMAND_ANSWER_TIMEOUT_MS = 5000L
    private val STATUS_TEMP_BASAL = 2
    private val STATUS_BOLUS = 3
    private val BOLUS_STATE_EXTENDED = 1
    private val BOLUS_STATE_DONE = 2
    private val HISTORY_BASAL = 0
    private val HISTORY_TEMP_BASAL = 1
    private val HISTORY_BOLUS = 2
    private val HISTORY_ALARM = 3
    private val HISTORY_WAIT_MS = 10_000L
    private val ALARM_SEEN_LIVE_MS = 120_000L
    @Volatile private var cancelTempBasalRunning = false
    @Volatile private var awaitingHistory = false
    @Volatile private var lastStatus: AaruyStatusSnapshot? = null

    /** One after the other and in the order they came, or a later status could overtake the log that explains it. */
    private val syncJobs by lazy {
        Channel<suspend () -> Unit>(Channel.UNLIMITED).also { jobs ->
            scope.launch {
                for (job in jobs) {
                    try {
                        job()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.PUMPCOMM, "Writing what the pump did failed", e)
                    }
                }
            }
        }
    }
    private val TAG = "AaruyBLEService"
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices

    val aaBleUtil = AaruyBleClient(context, aapsLogger, transport)

    /** On the thread of the pump events, because it shares their state. */
    private val handler by lazy { Handler(aaBleUtil.eventLooper) }

    /** Set when the emulator replaces the radio, see AaruyBindings. */
    val emulatorTransport: AaruyEmulatorBleTransport? = transport as? AaruyEmulatorBleTransport
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var connectDeviceName: String? = null
    @Volatile private var sendBasalsInfoOk = false
    @Volatile private var basalWanted: BasalInfo? = null
    private var sendBolusOk = false
    private var cancelBolusOk = false
    @Volatile private var sendTmpBasalOk = false
    @Volatile private var cancelTmpBasalOk = false
    private var startBasalOk = false
    private var isUnbinding = false
    private var extendedAnnounced = false
    private var connectCount: Int = 0

    var isConnected = false
    var isConnecting = false
    var receivingHistory: Boolean = false
    var refreshHistory: Boolean = false
    var isFirstDetection: Boolean = false

    /** Starts at the first connection: this class also exists for users of other pumps, and they need no check every 2 seconds. */
    private var detectionStarted = false

    private fun startDetection() {
        val detectionRunnable = object : Runnable {
            override fun run() {
                if (!isFirstDetection && !refreshHistory && receivingHistory) {
                    receivingHistory = false
                    if (aaruyPump.canSaveHistory)
                        requestHistoryData(aaruyPump.lastHistoryTime)
                }

                isFirstDetection = false
                if (connectCount > 0 && aaruyPump.connectionState != ConnectionState.CONNECTED) {
                    connectCount++
                    if (connectCount > 3) {
                        connectCount = 1
                        if (preferences.get(AaruyStringKey.MacAddress) != "") {
                            connect("retrying connection", preferences.get(AaruyStringKey.MacAddress))
                        }
                    }
                }
                else {
                    connectCount = 0
                }

                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(detectionRunnable, 2000)
    }

    @Synchronized
    fun connect(from: String, address: String?): Boolean {
        if (emulatorTransport == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
                        aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing Bluetooth ")
        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.")
            return false
        }

        if (address == null) {
            aapsLogger.error("unspecified address.")
            return false
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            aapsLogger.error("Device not found.  Unable to connect from: $from")
            return false
        }

        if (isConnected) {
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
            return true
        }

        aapsLogger.debug(LTag.PUMPBTCOMM, "Trying to create a new connection from: $from")
        connectDeviceName = if (emulatorTransport != null) AaruyEmulatorBleTransport.DEVICE_NAME else device.name
        isConnected = false
        isConnecting = true
        aaruyPump.connectionState = ConnectionState.CONNECTING
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
        connectCount = 1
        // Last: if the link is still up the AAR reports CONNECTED during this call, and nothing may overwrite that.
        connectDevice(device)
        return true
    }

    @Synchronized
    fun stopConnecting() {
        disconnect("stopConnecting")
        isConnecting = false
    }

    @Synchronized
    fun disconnect(from: String) {
        if (emulatorTransport == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")

        if (isConnecting) {
            isConnecting = false
            aaBleUtil.stopScanDevice()
            SystemClock.sleep(100)
        }

        if (bluetoothAdapter == null) {
            aapsLogger.error("disconnect is not possible: (mBluetoothAdapter == null) " + (bluetoothAdapter == null))
            isConnected = false
            return
        }

        aaBleUtil.disconnectDevice()
        isConnected = false
        SystemClock.sleep(2000)
    }

    @Synchronized
    fun unbindDevice() {
        isUnbinding = true
        aaBleUtil.unbindDevice()
        preferences.put(AaruyStringKey.PumpBoundAddress, "")
        preferences.put(AaruyStringKey.MacAddress, "")
        preferences.put(AaruyStringKey.PumpName, "")
        if (!isConnected) {
            aaruyPump.connectionState = ConnectionState.DISCONNECTING
            SystemClock.sleep(100)
            aaruyPump.connectionState = ConnectionState.DISCONNECTED
        }
        isConnected = false
        SystemClock.sleep(2000)
    }

    /** Pumps found so far are in [scannedDevices]. The emulator is always found. */
    @SuppressLint("MissingPermission")
    fun startScan() {
        aapsLogger.info(LTag.PUMP, "startScan")
        _scannedDevices.value = emptyList()
        if (emulatorTransport != null) {
            _scannedDevices.value = listOf(ScannedDevice(AaruyEmulatorBleTransport.DEVICE_NAME, AaruyEmulatorBleTransport.DEVICE_ADDRESS))
            return
        }
        aaBleUtil.scanDevice(object : ScanResultListener {
            override fun onScanResult(results: List<ScanResult>) {
                _scannedDevices.value = results.mapNotNull { result -> result.device.name?.let { ScannedDevice(it, result.device.address) } }
            }
        })
    }

    fun stopScan() {
        if (emulatorTransport == null) aaBleUtil.stopScanDevice()
    }

    /** A new pump: its old history is not read as new, and the connection that follows does the pairing. */
    fun pair(device: ScannedDevice) {
        aapsLogger.info(LTag.PUMP, "pair ${device.name}")
        preferences.put(AaruyStringKey.PumpBoundAddress, "")
        preferences.put(AaruyStringKey.MacAddress, device.address)
        preferences.put(AaruyStringKey.PumpName, device.name)
        aaruyPump.canSaveHistory = false
        aaruyPump.lastHistoryTime = 0
        aaruyPump.lastDeliveryEnd = 0
        aaruyPump.lastHistoryDailyTime = dateUtil.now()
        aaruyPump.lastBolusTime = 0
        aaruyPump.lastBolusAmount = 0.0
        rxBus.send(EventAaruyDeviceChange())
    }

    /** The pump answers each alarm it is told about. UNVERIFIED on a real pump: nothing has raised one there yet. */
    fun confirmAlarms() {
        aaruyPump.activeAlarms.toList().forEach { clearAlarmStatus(it.state) }
        aaruyPump.clearAlarmState()
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        if (!detectionStarted) {
            detectionStarted = true
            startDetection()
        }
        aapsLogger.warn(LTag.PUMPCOMM, "------------------>connectDevice device = $device")
        listenerReceiveData()
        aaBleUtil.bindDevice(device)
    }

    /**
     * Starts the emulator if the file `emulate_aaruy` is in the AAPS extra folder, as for the other pump emulators.
     */
    fun startEmulatorIfActive() {
        val transport = emulatorTransport ?: return
        // For tests: adb shell am broadcast -a app.aaps.pump.aaruy.emulator.OUT_OF_RANGE --ez value true
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val outOfRange = intent.getBooleanExtra("value", true)
                aapsLogger.warn(LTag.PUMPCOMM, "Aaruy emulator: out of range = $outOfRange")
                transport.outOfRange = outOfRange
                if (outOfRange) transport.loseConnection()
            }
        }, IntentFilter("app.aaps.pump.aaruy.emulator.OUT_OF_RANGE"), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun listenerReceiveData() {
        if (aaBleUtil.getOnBleListener() != null)
            return

        aapsLogger.warn(LTag.PUMPCOMM, "------------------>listenerReceiveData setOnBleListener")

        aaBleUtil.setOnBleListener(object : AaruyBleListener {
            override fun onBleStatusChanged(status: BleStatus) {
                aapsLogger.warn(LTag.PUMPCOMM, "aaruy onBleStatusChanged status = $status")
                if (status == BleStatus.CONNECTED) {
                    awaitingHistory = true
                    isConnected = true
                    isConnecting = false
                    isUnbinding = false
                    receivingHistory = false
                    connectCount = 0
                    aaBleUtil.stopScanDevice()
                    if (preferences.get(AaruyStringKey.PumpBoundAddress) == "") {
                        CoroutineScope(Dispatchers.IO).launch {
                            // 换泵保存
                            aapsLogger.warn(LTag.PUMPCOMM, "------------------>change pump")
                            val historyUnitInfo = HistoryUnitInfo()
                            historyUnitInfo.blueName = getShortDeviceName()
                            historyUnitInfo.type = 110
                            historyUnitInfo.time = System.currentTimeMillis()
                            historyUnitInfo.isUploaded = false
                            historyUnitInfoDao.insert(historyUnitInfo)
                        }
                    }
                    preferences.put(AaruyStringKey.PumpBoundAddress, preferences.get(AaruyStringKey.MacAddress))
                    aaruyPump.connectionState = ConnectionState.CONNECTED
                    // Anything can have happened while the link was down, a bolus may have ended.
                    syncPumpStatus()
                    // The log can hold what the status does not show. And a reply that was lost must not leave history off.
                    requestHistoryData(aaruyPump.lastHistoryTime)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                } else if(status == BleStatus.DISCONNECTED) {
                    // A stop with no answer on this link says nothing about how a bolus ended while the link was down.
                    aaruyPump.bolusStopRequested = false
                    isConnected = false
                    isConnecting = false
                    receivingHistory = false
                    aaruyPump.connectionState = ConnectionState.DISCONNECTED
                    if (isUnbinding) {
                        rxBus.send(EventAaruyDeviceChange())
                    }
                    if (preferences.get(AaruyStringKey.MacAddress) != "") {
                        connect("retrying connection", preferences.get(AaruyStringKey.MacAddress))
                    }
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                }
                else {
                    isConnected = false
                    isConnecting = true
                    aaruyPump.connectionState = ConnectionState.CONNECTING
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
                }
            }

            override fun onRequestResult(success: Boolean, paramId: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onRequestResult   $paramId($success)")
            }

            override fun onBolusValueTooHigh(value: Double, maxValue: Double) {
                aapsLogger.warn(LTag.PUMPCOMM, "onBolusValueTooHigh   value =  $value,maxValue = $maxValue)")
            }

            override fun onInjectTimeError(time: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onInjectTimeError   time = $time)")
            }

            override fun onRunBasalFirst(currentState: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onRunBasalFirst currentState = $currentState")
            }

            override fun onRunReplaceFirst(currentState: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onRunReplaceFirst currentState = $currentState")
            }

            override fun onRunTempBasalFirst(currentState: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onRunTempBasalFirst currentState = $currentState")
            }

            override fun onBolusIsRunning(currentState: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onBolusIsRunning currentState = $currentState")
            }

            override fun onLiquidShow() {
                aapsLogger.warn(LTag.PUMPCOMM, "onLiquidShow")
            }

            override fun onPumpClosedMode(currentMode: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onPumpClosedMode")
            }

            override fun onCurrentNoSuspend(currentState: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onCurrentNoSuspend currentState = $currentState")
            }

            override fun onReceiveBasicParamSetting(basicParam: BasicParam) {
                aapsLogger.warn(LTag.PUMPCOMM, "onReceiveBasicParamSetting basicParam = $basicParam")
                if (!aaruyPump.handleLoadBasicParam(basicParam) && aaruyPump.pumpState.isCanDoRefresh()) {
                    syncAndLoadBasicParam()
                }
            }

            override fun onReceiveBasalSetting(basalInfo: BasalInfo) {
                aapsLogger.warn(LTag.PUMPCOMM, "onReceiveBasalSetting basalInfo = $basalInfo")
                // The settings of a status read can still come, with the old profile. Only the new one is the answer.
                if (basalWanted?.let { aaruyPump.sameRates(it, basalInfo) } == true) sendBasalsInfoOk = true
                aaruyPump.actualBasalProfile = aaruyPump.dataClassToByteArray(basalInfo)
            }

            override fun onReceiveCurrentStatus(statusInfo: StatusInfo) {
                // aapsLogger.warn(LTag.PUMPCOMM, "onReceiveCurrentStatus statusInfo = $statusInfo")
                // Only an answer shows that the pump was reached.
                aaruyPump.lastConnection = dateUtil.now()
                onStatusForDatabase(statusInfo)
                val previousRate = aaruyPump.lastBasalRate
                aaruyPump.pumpState = AaruyPumpState.fromInt(statusInfo.runningStatus)
                aaruyPump.handleBasalStatusUpdate(0.0)

                when(statusInfo.runningStatus) {
                    0-> {       // 未运行
                        checkPumpInfusion()
                    }
                    1-> {       // 基础率运行
                        startBasalOk = true
                        if (statusInfo.basalStatus?.curValue != previousRate) {
                            if (aaruyPump.canSaveHistory)
                                requestHistoryData(aaruyPump.lastHistoryTime)
                        }
                        aaruyPump.handleBasalStatusUpdate(statusInfo.basalStatus?.curValue ?: 0.0)
                    }
                    2->{        // 临时基础率运行
                        aaruyPump.handleBasalStatusUpdate(statusInfo.tempBasalStatus?.curValue ?: 0.0)
                    }
                    3->{        // 大剂量运行
                        if (statusInfo.bolusStatus?.state == BOLUS_STATE_EXTENDED) {
                            onExtendedRunning()
                        } else {
                            sendBolusOk = true
                            val bolusCompleted = statusInfo.bolusStatus?.state == BOLUS_STATE_DONE
                            if (bolusCompleted && aaruyPump.canSaveHistory) requestHistoryData(aaruyPump.lastHistoryTime)
                            aaruyPump.handleBolusStatusUpdate(bolusCompleted, (statusInfo.bolusStatus?.curBolusNow ?: 0.0) / 1000)
                        }
                    }
                    4->{        // 排液操作正在进行

                    }
                    5->{        // 推杆回退正在进行
                        checkPumpInfusion()
                    }
                    6->{        // 推杆定位正在进行

                    }
                    7->{        // 暂停(推杆不回退)
                        if (aaruyPump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING) {
                            aaruyPump.handleBolusStatusUpdate(true)
                        }
                        if (aaruyPump.canSaveHistory)
                            requestHistoryData(aaruyPump.lastHistoryTime)
                        cancelBolusOk = true
                    }
                    8->{        // 停止(推杆回退)
                        checkPumpInfusion()
                        cancelBolusOk = true
                    }
                }
                // A real pump answers a stop by going from status 3 straight to 1, so any status other than 3 confirms it.
                if (statusInfo.runningStatus != 3) {
                    cancelBolusOk = true
                    extendedAnnounced = false
                }
                if (statusInfo.runningStatus != 3 && aaruyPump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING && !aaruyPump.bolusDone) {
                    if (aaruyPump.bolusStopRequested) aaruyPump.handleBolusStatusUpdate(true)
                    else aaruyPump.handleBolusEndNotSeen()
                }
                if (aaruyPump.pumpState != aaruyPump.pumpLastState &&
                    (aaruyPump.pumpLastState.isNormalStatus() || aaruyPump.pumpState.isNormalStatus())) {
                    if (aaruyPump.canSaveHistory)
                        requestHistoryData(aaruyPump.lastHistoryTime)
                }
                aaruyPump.pumpLastState = aaruyPump.pumpState
                aaruyPump.reservoir = statusInfo.remainAmount.toDouble()
                if (!aaruyPump.alreadyPushBack && statusInfo.remainAmount == 0) {
                    aaruyPump.alreadyPushBack = true
                    aaruyPump.pumpWarning = AlarmState.PUMP_ZERO_RESERVOIR
                    replaceDevice()
                }
            }

            override fun onReceiveHistoryLog(logInfo: LogInfo) {
                aapsLogger.warn(LTag.PUMPCOMM, "onReceiveHistoryLog logInfo = $logInfo")
                onHistoryLog(logInfo)
            }

            override fun onReceiveHistoryDaily(dailyInfo: DailyInfo) {
                aapsLogger.warn(LTag.PUMPCOMM, "onReceiveHistoryDaily dailyInfo = $dailyInfo")
                aaruyPump.lastHistoryDailyTime = dailyInfo.dailyItemList.last().timestamp
                saveDailyData(dailyInfo)
                if (refreshHistory) {
                    receivingHistory = false
                }
            }

            override fun onReceiveRealData(battery: Int, signal: Byte) {
                aapsLogger.warn(LTag.PUMPCOMM, "onReceiveRealData battery = $battery,signal = $signal")
                aaruyPump.remainBattery = battery.toDouble()
            }

            override fun onReceiveErrorData(errorType: Int, error: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onReceiveErrorData errorType = $errorType,error = $error")
                val tmpErrCode = (error shl 8) or (errorType)
                aaruyPump.errCode.tryEmit(tmpErrCode)
                when(error) {
                    0->{        // 操作的正常提示
                        when(errorType) {
                            16-> {  // 进行了推杆定位的排液操作成功

                            }
                            17-> {  // 推杆回退成功

                            }
                            25-> {  // 有液体排出

                            }
                        }
                    }
                    0x80->{     // 操作的错误提示
                        if (tmpErrCode == 0x801C) {
                            if(receivingHistory) {
                                receivingHistory = false
                            }
                        }
                        val alarmState = AlarmState.fromInt(tmpErrCode)
                        if (alarmState != AlarmState.NONE) {
                            aaruyPump.pumpWarning = alarmState
                        }
                    }
                    0x81->{     // 报警提示类型
                        val alarmState = AlarmState.fromInt(tmpErrCode)
                        if (!aaruyPump.activeAlarms.contains(alarmState) && alarmState != AlarmState.NONE) {
                            if(AlarmState.isHighAlarm(alarmState) || AlarmState.isLowAlarm(alarmState))
                                aaruyPump.addAlarm(alarmState)
                            aaruyPump.pumpWarning = alarmState
                        }
                        if (aaruyPump.canSaveHistory)
                            requestHistoryData(aaruyPump.lastHistoryTime)
                    }
                }
            }

            override fun onReceiveConfirmWarn(confirm: Int) {
                aapsLogger.warn(LTag.PUMPCOMM, "onReceiveConfirmWarn confirm = $confirm")
            }

            override fun onUnbindClearInfo() {
                aapsLogger.warn(LTag.PUMPCOMM, "onUnbindClearInfo")
                preferences.put(AaruyStringKey.PumpBoundAddress, "")
                preferences.put(AaruyStringKey.MacAddress, "")
                preferences.put(AaruyStringKey.PumpName, "")
            }

            // override fun onReceiveReportDevice(reportDeviceInfo: ReportDeviceInfo) {
            //     aapsLogger.warn(LTag.PUMPCOMM, "onReceiveReportDevice reportDeviceInfo is $reportDeviceInfo")
            // }
        })
    }

    internal fun onHistoryLog(logInfo: LogInfo) {
        refreshHistory = true
        if (aaruyPump.canSaveHistory) {
            aapsLogger.warn(LTag.PUMPCOMM, "aaruyPump.canSaveHistory curPage = ${logInfo.curPage}, totalPage = ${logInfo.totalPage}")
            saveHistoryData(logInfo)
            if (logInfo.curPage + 1 == logInfo.totalPage) {
                readDailyData(aaruyPump.lastHistoryDailyTime)
                onHistoryRead()
            }
        }
        else if (logInfo.curPage + 1 == logInfo.totalPage) {
            aapsLogger.warn(LTag.PUMPCOMM, "aaruyPump.canSaveHistory canSaveHistory = ${aaruyPump.canSaveHistory}, totalPage = ${logInfo.totalPage}")
            aaruyPump.lastHistoryTime = logInfo.logItems.last().timestamp
            aaruyPump.canSaveHistory = true
            receivingHistory = false
            onHistoryRead()
        }

        if (logInfo.curPage == logInfo.totalPage && logInfo.curPage == 0) {
            // A real pump has no history after pairing. Nothing else would turn history on, and then it is never read.
            aaruyPump.canSaveHistory = true
            readDailyData(aaruyPump.lastHistoryDailyTime)
            receivingHistory = false
            onHistoryRead()
        }
    }

    /** AAPS never starts an extended bolus, so something else did. One announcement, and the bolus state of the driver stays as it is. */
    internal fun onExtendedRunning() {
        if (extendedAnnounced) return
        extendedAnnounced = true
        aapsLogger.warn(LTag.PUMPCOMM, "The pump runs an extended bolus that AAPS did not start")
        scope.launch { aaruyPump.announceExtendedNotFromAaps() }
    }

    private fun checkPumpInfusion() {
        if (aaruyPump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING) {
            aaruyPump.handleBolusStatusUpdate(true)
        }
        else if (aaruyPump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING || aaruyPump.pumpLastState == AaruyPumpState.TMPBASALRATE_RUNNING) {
            aaruyPump.handleBasalStatusUpdate(0.0)
        }
    }

    /** Seen on a real pump: a new profile is answered after a full second. A fixed short wait calls a good command failed, and the pump then does what AAPS does not know. */
    private fun waitForAnswer(answered: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + COMMAND_ANSWER_TIMEOUT_MS
        while (!answered() && System.currentTimeMillis() < end) SystemClock.sleep(50)
        return answered()
    }

    fun updateBasalsInPump(basalInfo: BasalInfo): Boolean {
        sendBasalsInfoOk = false
        basalWanted = basalInfo
        aaBleUtil.setupBasalInfo(basalInfo)
        return waitForAnswer { sendBasalsInfoOk }
    }

    fun setBolus(insulin: Double, isSmb: Boolean = true): Boolean {
        sendBolusOk = false
        val startBolusTime = dateUtil.now()
        aaBleUtil.setupAndStartBolus(insulin, 0.0, 0, isSmb)
        // A status that comes late must not report a failure while the pump delivers: the user could bolus again.
        val end = System.currentTimeMillis() + BOLUS_START_TIMEOUT_MS
        while (!sendBolusOk && System.currentTimeMillis() < end) SystemClock.sleep(100)
        if (sendBolusOk) {
            aaruyPump.curBolusTime = startBolusTime
        }
        return sendBolusOk
    }

    fun cancelBolus(): Boolean {
        cancelBolusOk = false
        aaBleUtil.cancelBolus()
        val end = System.currentTimeMillis() + BOLUS_STOP_TIMEOUT_MS
        while (!cancelBolusOk && System.currentTimeMillis() < end) SystemClock.sleep(20)
        if (cancelBolusOk) {
            aaBleUtil.continueInject()
        }
        return cancelBolusOk
    }

    fun setTempBasal(absoluteRate: Double, durationInMinutes: Int): Boolean {
        sendTmpBasalOk = false
        aaBleUtil.setClosedMode(1)
        SystemClock.sleep(200)
        aaBleUtil.startTempBasal(absoluteRate, durationInMinutes)
        return waitForAnswer { sendTmpBasalOk }
    }

    fun cancelTempBasal(): Boolean {
        cancelTmpBasalOk = false
        cancelTempBasalRunning = true
        aaBleUtil.cancelTempBasal()
        val answered = waitForAnswer { cancelTmpBasalOk }
        cancelTempBasalRunning = false
        return answered
    }

    /** After a reconnect the loop must not decide on what the driver knew before the log was read. */
    fun waitForHistoryAfterReconnect() {
        val end = System.currentTimeMillis() + HISTORY_WAIT_MS
        while (awaitingHistory && System.currentTimeMillis() < end) SystemClock.sleep(50)
    }

    /** The flags that end a waiting command come after the write, so the command finds the result in the database. */
    private fun onStatusForDatabase(statusInfo: StatusInfo) {
        val snapshot = AaruyStatusSnapshot(
            status = statusInfo.runningStatus,
            rate = statusInfo.tempBasalStatus?.curValue ?: 0.0,
            remainingSeconds = statusInfo.tempBasalStatus?.remainTime ?: 0,
            runningSeconds = statusInfo.tempBasalStatus?.runningTime ?: 0
        )
        lastStatus = snapshot
        val live = !awaitingHistory || cancelTempBasalRunning
        syncJobs.trySend {
            tempBasalSync.onStatus(snapshot, live)
            if (snapshot.status == STATUS_TEMP_BASAL) sendTmpBasalOk = true
            else if (snapshot.status != STATUS_BOLUS) cancelTmpBasalOk = true
        }
    }

    /** The log is read. What the status could not decide before can be decided now. */
    private fun onHistoryRead() {
        syncJobs.trySend {
            awaitingHistory = false
            lastStatus?.let { tempBasalSync.onStatus(it, live = true) }
        }
    }

fun syncAndLoadBasicParam() {
        val basicParam = BasicParam (
            aaruyPump.maxBasal,
            aaruyPump.maxBolus,
            aaruyPump.bolusStep,
            aaruyPump.lowAlert,
            aaruyPump.closeInjectSound,
            aaruyPump.pumpType,
        )
        aaBleUtil.setupBasicParamInfo(basicParam)
    }

    fun syncCurTimeToPump() {
        aaBleUtil.updatePumpTime()
    }

    fun syncPumpStatus() {
        aaBleUtil.getCurStatus()
    }

    fun replaceDevice() {
        aaBleUtil.replaceDevice()
    }

    fun pushForward() {
        // aaBleUtil.drainLiquid()
        aaBleUtil.pushPosition()
    }

    fun needlePushAir(softNeedleType: Byte) {
        aaBleUtil.needlePushAir(softNeedleType)
    }

    fun startBaseInject():Boolean {
        startBasalOk = false
        aaBleUtil.startBasal()
        SystemClock.sleep(500)
        return startBasalOk
    }

    fun clearAlarmStatus(errorType: Int) {
        aaBleUtil.confirmReply(errorType)
        SystemClock.sleep(500)
    }

    fun getPumpVersionNum(): String {
        return aaBleUtil.getPumpVersionNum()
    }

    fun requestHistoryData(lastTime: Long) {
        aapsLogger.warn(LTag.PUMPCOMM, "requestHistoryData lastTime = $lastTime, receivingHistory is $receivingHistory refreshHistory is $refreshHistory, isFirstDetection is $isFirstDetection")
        if (!receivingHistory) {
            isFirstDetection = true
            refreshHistory = false
            aapsLogger.warn(LTag.PUMPCOMM, "requestHistoryData receivingHistory = $receivingHistory")
            receivingHistory = true
            CoroutineScope(Dispatchers.Default).launch {
                delay(20)
                aaBleUtil.getHistoryLogData(lastTime)
            }
        }
    }

    fun readDailyData(lastTime: Long) {
        CoroutineScope(Dispatchers.Default).launch {
            delay(20)
            aaBleUtil.getHistoryDailyData(lastTime)
        }
    }

    fun getShortDeviceName(): String {
        var name = "Q10000"
        if (preferences.get(AaruyStringKey.PumpBoundAddress) == "") {
            name = preferences.get(AaruyStringKey.MacAddress)         // 将要连接的泵
        }
        else {
            name = aaruyPump.pumpSN
        }
        return if (name.length > 6) {
            (name.substring(6))
        } else {
            name
        }
    }

    private fun saveHistoryData(logInfo: LogInfo) {
        val lastPage = logInfo.curPage + 1 == logInfo.totalPage
        syncJobs.trySend {
            logInfo.logItems.forEachIndexed { index, item ->
                aapsLogger.warn(LTag.PUMPCOMM, "saveHistoryData item.data = ${item.data}, item.delayBolus is ${item.delayBolus}")
                val historyUnitInfo = HistoryUnitInfo()
                historyUnitInfo.data = item.data.toLong()
                historyUnitInfo.delayBolus = item.delayBolus.toLong()
                historyUnitInfo.injectTime = item.injectTime.toLong()
                historyUnitInfo.reserved = item.reserved
                if (item.delayBolus.toInt() == 0 && item.type.toInt() == 2) {
                    val bolusStep =
                        aaruyPump.bolusStep.times(AaruySetting.precision).toLong()
                    historyUnitInfo.immTime =
                        item.data.toLong().div(bolusStep)
                            .times(2)
                }
                historyUnitInfo.time = item.timestamp
                historyUnitInfo.type = item.type.toInt()
                historyUnitInfo.blueName = getShortDeviceName()
                historyUnitInfo.isUploaded = false
                historyUnitInfo.pumpType = aaruyPump.pumpType

                if (item.type == 2 && item.delayBolus.toInt() == 0) {
                    aaruyPump.lastBolusAmount = item.data / 1000.0
                    aaruyPump.lastBolusTime = item.timestamp

                    val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(item.timestamp, item.data / 1000.0)
                    var newRecord = false
                    if (detailedBolusInfo != null) {
                        val syncOk = pumpSync.syncBolusWithTempId(
                            timestamp = item.timestamp,
                            amount = PumpInsulin(item.data / 1000.0),
                            temporaryId = detailedBolusInfo.timestamp,
                            type = detailedBolusInfo.bolusType,
                            pumpId = item.timestamp,
                            pumpType = aaruyPump.pumpType(),
                            pumpSerial = aaruyPump.pumpSN
                        )
                        if (!syncOk) {
                            aapsLogger.warn(LTag.PUMPCOMM, "saveHistoryData: BOLUS_RECORD: Failed to sync bolus with tempId: ${detailedBolusInfo.timestamp}")
                            // detailedInfo can be from another similar record. Reinsert
                            detailedBolusInfoStorage.add(detailedBolusInfo)
                        }
                        else {
                            aapsLogger.debug(
                                LTag.PUMPCOMM,
                                "EVENT syncBolusWithTempId ok timestamp ${dateUtil.dateAndTimeString(aaruyPump.lastBolusTime)} Bolus: ${aaruyPump.lastBolusAmount}U "
                            )
                        }
                    } else {
                        newRecord = pumpSync.syncBolusWithPumpId(
                            timestamp = item.timestamp,
                            amount = PumpInsulin(item.data / 1000.0),
                            type = null,
                            pumpId = item.timestamp,
                            pumpType = aaruyPump.pumpType(),
                            pumpSerial = aaruyPump.pumpSN
                        )
                    }
                    aapsLogger.debug(
                        LTag.PUMPCOMM,
                        "from record: ${newRecordInfo(newRecord)}EVENT BOLUS ${dateUtil.dateAndTimeString(item.timestamp)} ($item.timestamp) Bolus: ${item.data / 1000.0}U "
                    )
                }
                syncFromHistory(item, logInfo.logItems.getOrNull(index + 1)?.timestamp)
                if (historyUnitInfoDao.count(item.timestamp, historyUnitInfo.type, historyUnitInfo.reserved) == 0) historyUnitInfoDao.insert(historyUnitInfo)
            }
            // Only now: an entry not yet written must come again if the app stops here.
            if (lastPage && logInfo.logItems.isNotEmpty()) {
                aaruyPump.lastHistoryTime = logInfo.logItems.last().timestamp
                receivingHistory = false
            }
        }
    }

    /** What the pump log knows and the driver may not: all of this can happen while the link is down. */
    private suspend fun syncFromHistory(item: LogItem, nextStart: Long?) {
        val minutes = T.mins(item.injectTime.toLong()).msecs()
        when (item.type.toInt()) {
            HISTORY_BASAL      -> tempBasalSync.onHistoryDelivery(item.timestamp, item.timestamp + minutes)

            HISTORY_TEMP_BASAL -> {
                tempBasalSync.onHistoryDelivery(item.timestamp, item.timestamp + minutes)
                tempBasalSync.onHistoryTempBasal(item, nextStart, lastStatus?.status)
            }

            HISTORY_BOLUS      -> if (item.delayBolus.toInt() == 0) {
                // Seen on a real pump: no basal entry runs during a bolus, so this time is no gap. One step every 2 seconds.
                val steps = item.data.toLong() / (aaruyPump.bolusStep * AaruySetting.precision)
                tempBasalSync.onHistoryDelivery(item.timestamp, item.timestamp + T.secs(steps * 2).msecs())
            }

            HISTORY_ALARM      -> {
                // A newer one was announced when it came. Only one from the time without a link is new.
                if (dateUtil.now() - item.timestamp < ALARM_SEEN_LIVE_MS) return
                val alarm = AlarmState.fromInt(item.data.toInt())
                if (alarm != AlarmState.NONE) pumpSync.insertAnnouncement(aaruyPump.alarmStateToString(alarm), item.timestamp, aaruyPump.pumpType(), aaruyPump.pumpSN)
            }
        }
    }

    internal fun saveDailyData(dailyInfo: DailyInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentPumpName = getShortDeviceName()
            for (item in dailyInfo.dailyItemList) {
                aapsLogger.warn(LTag.PUMPCOMM, "saveDailyData item.dailyBolus = ${item.dailyBolus}, item.dailyTotal is ${item.dailyTotal}")
                // The pump sends today again and again while the day grows, so one day of one pump is one row.
                val row = historyDailyUnitInfoDao.find(item.timestamp, currentPumpName)
                val info = row ?: HistoryDailyUnitInfo().apply {
                    dateTimestamp = item.timestamp
                    pumpName = currentPumpName
                }
                info.dailyBolus = (item.dailyBolus * 10).roundToInt()
                info.dailyTotal = (item.dailyTotal * 10).roundToInt()
                info.isUploaded = false
                // Only for the history screen. The day of the pump starts at midnight in China, so this is no TDD of a local day.
                if (row == null) historyDailyUnitInfoDao.insert(info) else historyDailyUnitInfoDao.update(info)
            }
        }

    }

    private fun newRecordInfo(newRecord: Boolean): String {
        return "${if (newRecord) "**NEW** " else ""}"
    }
}