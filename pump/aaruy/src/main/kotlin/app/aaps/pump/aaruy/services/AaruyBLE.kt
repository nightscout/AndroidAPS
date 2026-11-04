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
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.aaruy.comm.AaruyBleClient
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.comm.DailyInfo
import app.aaps.pump.aaruy.comm.LogInfo
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
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfo
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.ui.event.EventAaruyDeviceChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nordicsemi.android.support.v18.scanner.ScanResult
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.aaruy.keys.AaruyStringKey

interface BLECommCallback {
    fun onBLEScan()
}

@Singleton
class AaruyBLE @Inject internal constructor(
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
    private val fileListProvider: FileListProvider,
) {
    private val EMULATOR_MARKER = "emulate_aaruy"
    private val TAG = "AaruyBLEService"
    val scanResultList = ArrayList<ScanResult>()
    private val handler = Handler(Looper.getMainLooper())

    val aaBleUtil = AaruyBleClient(context, aapsLogger)

    /** TEMPORARY: the emulator instead of the radio. The final hook is planned for the v4 driver. */
    var emulatorTransport: AaruyEmulatorBleTransport? = null
        private set
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var connectDeviceName: String? = null
    private var sendBasalsInfoOk = false
    private var sendBolusOk = false
    private var sendExtendedBolusOk = false
    private var cancelBolusOk = false
    private var sendTmpBasalOk = false
    private var cancelTmpBasalOk = false
    private var startBasalOk = false
    private var isUnbinding = false
    private var mCallback: BLECommCallback? = null
    private var bolusType: Int = 0
    private var connectCount: Int = 0

    var isConnected = false
    var isConnecting = false
    var receivingHistory: Boolean = false
    var refreshHistory: Boolean = false
    var isFirstDetection: Boolean = false

    init {
        startDetection()
    }

    fun setCallback(callback: BLECommCallback?) {
        this.mCallback = callback
    }

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
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
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

    @SuppressLint("MissingPermission")
    fun scanDevice() {
        aapsLogger.info(LTag.PUMP, "scanDevice")
        aaBleUtil.scanDevice(object : ScanResultListener {
            override fun onScanResult(results: List<ScanResult>) {
                scanResultList.clear()
                scanResultList.addAll(results)
                mCallback?.onBLEScan()
            }

        })
    }

    fun stopScan() {
        aaBleUtil.stopScanDevice()
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        aapsLogger.warn(LTag.PUMPCOMM, "------------------>connectDevice device = $device")
        listenerReceiveData()
        if (emulatorTransport != null) aaBleUtil.connectWithoutBonding(device) else aaBleUtil.bindDevice(device)
    }

    /**
     * Starts the emulator if the file `emulate_aaruy` is in the AAPS extra folder, as for the other pump emulators.
     * It also sets a pump address, because the emulator replaces the scan and pairing screen.
     */
    fun startEmulatorIfRequested() {
        if (emulatorTransport != null || fileListProvider.ensureExtraDirExists()?.findFile(EMULATOR_MARKER) == null) return
        aapsLogger.warn(LTag.PUMPCOMM, "Aaruy pump EMULATOR is active. No insulin is delivered.")
        val emulator = AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })
        val transport = AaruyEmulatorBleTransport(emulator)
        emulatorTransport = transport
        aaBleUtil.useTransport(transport)
        // For tests: adb shell am broadcast -a app.aaps.pump.aaruy.emulator.OUT_OF_RANGE --ez value true
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val outOfRange = intent.getBooleanExtra("value", true)
                aapsLogger.warn(LTag.PUMPCOMM, "Aaruy emulator: out of range = $outOfRange")
                transport.outOfRange = outOfRange
                if (outOfRange) transport.loseConnection()
            }
        }, IntentFilter("app.aaps.pump.aaruy.emulator.OUT_OF_RANGE"), ContextCompat.RECEIVER_EXPORTED)
        preferences.put(AaruyStringKey.MacAddress, AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        preferences.put(AaruyStringKey.PumpBoundAddress, AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        preferences.put(AaruyStringKey.PumpName, AaruyEmulatorBleTransport.DEVICE_NAME)
    }

    private fun listenerReceiveData() {
        if (aaBleUtil.getOnBleListener() != null)
            return

        aapsLogger.warn(LTag.PUMPCOMM, "------------------>listenerReceiveData setOnBleListener")

        aaBleUtil.setOnBleListener(object : AaruyBleListener {
            override fun onBleStatusChanged(status: BleStatus) {
                aapsLogger.warn(LTag.PUMPCOMM, "aaruy onBleStatusChanged status = $status")
                if (status == BleStatus.CONNECTED) {
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
                    // The scan screen asks for the first history, and the emulator replaces that screen.
                    if (emulatorTransport != null && !aaruyPump.canSaveHistory) requestHistoryData(aaruyPump.lastHistoryTime)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                } else if(status == BleStatus.DISCONNECTED) {
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
                sendBasalsInfoOk = true
                aaruyPump.actualBasalProfile = aaruyPump.dataClassToByteArray(basalInfo)
            }

            override fun onReceiveCurrentStatus(statusInfo: StatusInfo) {
                // aapsLogger.warn(LTag.PUMPCOMM, "onReceiveCurrentStatus statusInfo = $statusInfo")
                aaruyPump.pumpState = AaruyPumpState.fromInt(statusInfo.runningStatus)
                aaruyPump.handleBasalStatusUpdate(0.0)

                when(statusInfo.runningStatus) {
                    0-> {       // 未运行
                        checkPumpInfusion()
                    }
                    1-> {       // 基础率运行
                        if(!cancelTmpBasalOk)
                            cancelTmpBasalOk = true
                        startBasalOk = true
                        if (statusInfo.basalStatus?.curValue != aaruyPump.lastBasalRate) {
                            if (aaruyPump.canSaveHistory)
                                requestHistoryData(aaruyPump.lastHistoryTime)
                        }
                        aaruyPump.handleBasalStatusUpdate(statusInfo.basalStatus?.curValue ?: 0.0)
                    }
                    2->{        // 临时基础率运行
                        sendTmpBasalOk = true
                        aaruyPump.handleBasalStatusUpdate(statusInfo.tempBasalStatus?.curValue ?: 0.0)
                    }
                    3->{        // 大剂量运行
                        sendBolusOk = true
                        var bolusCompleted: Boolean = false
                        var amountDelivered: Double = 0.0

                        amountDelivered = statusInfo.bolusStatus?.curBolusNow ?: 0.0
                        amountDelivered /= 1000
                        if (statusInfo.bolusStatus?.state == 1) {
                            bolusType = 1
                            sendExtendedBolusOk = true
                        }
                        else if (statusInfo.bolusStatus?.state == 2) {
                            bolusCompleted = true
                            if (aaruyPump.canSaveHistory)
                                requestHistoryData(aaruyPump.lastHistoryTime)
                        }
                        else {
                            bolusType = 0
                        }
                        aaruyPump.handleBolusStatusUpdate(bolusType, bolusCompleted, amountDelivered)
                    }
                    4->{        // 排液操作正在进行

                    }
                    5->{        // 推杆回退正在进行
                        checkPumpInfusion()
                    }
                    6->{        // 推杆定位正在进行

                    }
                    7->{        // 暂停(推杆不回退)
                        if (aaruyPump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING && bolusType == 0) {
                            aaruyPump.handleBolusStatusUpdate(0, true)
                        }
                        if (aaruyPump.canSaveHistory)
                            requestHistoryData(aaruyPump.lastHistoryTime)
                        cancelBolusOk = true
                        cancelTmpBasalOk = true
                    }
                    8->{        // 停止(推杆回退)
                        checkPumpInfusion()
                        cancelBolusOk = true
                        cancelTmpBasalOk = true
                    }
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
                refreshHistory = true
                if (aaruyPump.canSaveHistory) {
                    aapsLogger.warn(LTag.PUMPCOMM, "aaruyPump.canSaveHistory curPage = ${logInfo.curPage}, totalPage = ${logInfo.totalPage}")
                    saveHistoryData(logInfo)
                    if (logInfo.curPage+1 == logInfo.totalPage) {
                        readDailyData(aaruyPump.lastHistoryDailyTime)
                        aaruyPump.lastHistoryTime = logInfo.logItems.last().timestamp
                    }
                }
                else if(logInfo.curPage+1 == logInfo.totalPage) {
                    aapsLogger.warn(LTag.PUMPCOMM, "aaruyPump.canSaveHistory canSaveHistory = ${aaruyPump.canSaveHistory}, totalPage = ${logInfo.totalPage}")
                    aaruyPump.lastHistoryTime = logInfo.logItems.last().timestamp
                    aaruyPump.canSaveHistory = true
                    receivingHistory = false
                }

                if (logInfo.curPage == logInfo.totalPage && logInfo.curPage == 0) {
                    readDailyData(aaruyPump.lastHistoryDailyTime)
                    receivingHistory = false
                }
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
                aaruyPump.errCode.postValue(tmpErrCode)
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

    // 异常中止注射需调用此函数
    private fun checkPumpInfusion() {
        if (aaruyPump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING) {
            if (bolusType == 0) {
                aaruyPump.handleBolusStatusUpdate(0, true)
            }
            else {
                val endTime = dateUtil.now()
                aaruyPump.syncStopExtendedWithPumpId(endTime)
            }
        }
        else if (aaruyPump.pumpLastState == AaruyPumpState.TMPBASALRATE_RUNNING) {
            val endTime = dateUtil.now()
            aaruyPump.syncStopTBRWithPumpId(endTime)
            aaruyPump.handleBasalStatusUpdate(0.0)
        }
        else if (aaruyPump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING) {
            aaruyPump.handleBasalStatusUpdate(0.0)
        }
    }

    fun updateBasalsInPump(basalInfo: BasalInfo): Boolean {
        sendBasalsInfoOk = false
        aaBleUtil.setupBasalInfo(basalInfo)
        SystemClock.sleep(500)
        return sendBasalsInfoOk
    }

    fun setBolus(insulin: Double, isSmb: Boolean = true): Boolean {
        sendBolusOk = false
        val startBolusTime = dateUtil.now()
        aaBleUtil.setupAndStartBolus(insulin, 0.0, 0, isSmb)
        SystemClock.sleep(500)
        if (sendBolusOk) {
            aaruyPump.curBolusTime = startBolusTime
        }
        return sendBolusOk
    }

    fun cancelBolus(): Boolean {
        cancelBolusOk = false
        val endTime = dateUtil.now()
        aaBleUtil.cancelBolus()
        SystemClock.sleep(500)
        if (cancelBolusOk) {
            if (bolusType == 1) {
                aaruyPump.syncStopExtendedWithPumpId(endTime)
            }
            aaBleUtil.continueInject()
        }
        return cancelBolusOk
    }

    fun setTempBasal(absoluteRate: Double, durationInMinutes: Int): Boolean {
        sendTmpBasalOk = false
        aaBleUtil.setClosedMode(1)
        SystemClock.sleep(200)
        val startTime = dateUtil.now()
        aaBleUtil.startTempBasal(absoluteRate, durationInMinutes)
        SystemClock.sleep(500)
        if (sendTmpBasalOk) {
            aaruyPump.syncTBRWithPumpId(startTime, absoluteRate,durationInMinutes.toLong()*60*1000)
        }
        return sendTmpBasalOk
    }

    fun cancelTempBasal(): Boolean {
        cancelTmpBasalOk = false
        val endTime = dateUtil.now()
        aaBleUtil.cancelTempBasal()
        SystemClock.sleep(500)
        if (cancelTmpBasalOk) {
            aaruyPump.syncStopTBRWithPumpId(endTime)
        }
        return cancelTmpBasalOk
    }

    fun extendedBolus(insulin: Double, durationInMinutes: Int): Boolean {
        sendExtendedBolusOk = false
        val startTime = dateUtil.now()
        aaBleUtil.setupAndStartBolus(0.0, insulin, durationInMinutes, false)
        SystemClock.sleep(500)
        if(sendExtendedBolusOk) {
            aaruyPump.syncExtendedWithPumpId(startTime, insulin, durationInMinutes.toLong()*60*1000)
        }
        return sendExtendedBolusOk
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
        CoroutineScope(Dispatchers.IO).launch {
            for (item in logInfo.logItems) {
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
                            amount = item.data / 1000.0,
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
                            amount = item.data / 1000.0,
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
                historyUnitInfoDao.insert(historyUnitInfo)
            }
        }
    }

    private fun saveDailyData(dailyInfo: DailyInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentPumpName = getShortDeviceName()
            for (item in dailyInfo.dailyItemList) {
                aapsLogger.warn(LTag.PUMPCOMM, "saveDailyData item.dailyBolus = ${item.dailyBolus}, item.dailyTotal is ${item.dailyTotal}")
                val allData = historyDailyUnitInfoDao.queryAll()
                var isExist = false
                if (allData.isEmpty()) {
                    val historyDailyUnitInfo = HistoryDailyUnitInfo()
                    historyDailyUnitInfo.dailyBolus = (item.dailyBolus*10).toInt()
                    historyDailyUnitInfo.dateTimestamp = item.timestamp
                    historyDailyUnitInfo.dailyTotal = (item.dailyTotal*10).toInt()
                    historyDailyUnitInfo.pumpName = currentPumpName
                    historyDailyUnitInfo.isReplace = false
                    historyDailyUnitInfo.isUploaded = false
                    historyDailyUnitInfoDao.insert(historyDailyUnitInfo)
                } else {
                    for (i in allData.indices) {
                        val data = allData[i]
                        if (i < 3) {
                            aapsLogger.warn(LTag.PUMPCOMM, "history ${data.toString()}")
                            if (!currentPumpName.contains(data.pumpName)) {
                                val historyDailyUnitInfo = HistoryDailyUnitInfo()
                                historyDailyUnitInfo.dailyBolus = (item.dailyBolus*10).toInt()
                                historyDailyUnitInfo.dateTimestamp = item.timestamp
                                historyDailyUnitInfo.dailyTotal = (item.dailyTotal*10).toInt()
                                historyDailyUnitInfo.pumpName = currentPumpName
                                historyDailyUnitInfo.isReplace = false
                                historyDailyUnitInfo.isUploaded = false
                                historyDailyUnitInfoDao.insert(historyDailyUnitInfo)
                                isExist = true
                                break
                            } else {
                                if (data.dateTimestamp == item.timestamp) {
                                    isExist = true
                                    aapsLogger.warn(LTag.PUMPCOMM, "find timestamp = ${item.timestamp}")
                                    data.dailyBolus = (item.dailyBolus*10).toInt()
                                    data.dailyTotal = (item.dailyTotal*10).toInt()
                                    data.isUploaded = false
                                    historyDailyUnitInfoDao.update(data)
                                    break
                                }
                            }
                        } else {
                            break
                        }
                    }
                    aapsLogger.warn(LTag.PUMPCOMM, "history isExist = $isExist")
                    if (!isExist) {
                        val historyDailyUnitInfo = HistoryDailyUnitInfo()
                        historyDailyUnitInfo.dailyBolus = (item.dailyBolus*10).toInt()
                        historyDailyUnitInfo.dateTimestamp = item.timestamp
                        historyDailyUnitInfo.dailyTotal = (item.dailyTotal*10).toInt()
                        historyDailyUnitInfo.pumpName = currentPumpName
                        historyDailyUnitInfo.isReplace = false
                        historyDailyUnitInfo.isUploaded = false
                        historyDailyUnitInfoDao.insert(historyDailyUnitInfo)
                    }
                }
                // it is a TDD, store it for stats also.
                pumpSync.createOrUpdateTotalDailyDose(
                    timestamp = item.timestamp,
                    bolusAmount = item.dailyBolus,
                    basalAmount = item.dailyTotal - item.dailyBolus,
                    totalAmount = item.dailyTotal,
                    pumpId = null,
                    pumpType = aaruyPump.pumpType(),
                    pumpSerial = aaruyPump.pumpSN
                )
            }
        }

    }

    private fun newRecordInfo(newRecord: Boolean): String {
        return "${if (newRecord) "**NEW** " else ""}"
    }
}