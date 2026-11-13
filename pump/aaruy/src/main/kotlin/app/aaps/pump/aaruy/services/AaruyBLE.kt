package app.aaps.pump.aaruy.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
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
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.common.util.AaruySetting
import app.aaps.pump.aaruy.compose.AaruyHistoryTypes
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfo
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.events.EventAaruyDeviceChange
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import app.aaps.pump.aaruy.keys.AaruyStringKey

@SingleIn(AppScope::class)
@Inject
class AaruyBLE(
    private val transport: AaruyBleTransport,
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
    private val notificationManager: NotificationManager,
    private val rh: ResourceHelper,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val BOLUS_START_TIMEOUT_MS = 5000L
    private val BOLUS_STOP_TIMEOUT_MS = 500L
    private val COMMAND_ANSWER_TIMEOUT_MS = 5000L
    /** A frame sent just before the driver gave up on a temp basal is answered within this. */
    private val ANSWER_AFTER_GIVING_UP_MS = 1000L
    private val STATUS_BASAL = 1
    private val STATUS_TEMP_BASAL = 2
    private val STATUS_BOLUS = 3
    private val BOLUS_STATE_EXTENDED = 1
    private val BOLUS_STATE_DONE = 2
    private val BOLUS_TYPE_SMB = 2
    private val BOLUS_SECONDS_PER_STEP = 2.0
    private val TEMP_BASAL_PARAM = 8
    private val BOLUS_CONTROL_PARAM = 9
    private val HISTORY_BASAL = 0
    private val HISTORY_TEMP_BASAL = 1
    private val HISTORY_BOLUS = 2
    private val HISTORY_ALARM = 3
    private val HISTORY_WAIT_MS = 10_000L
    private val ALARM_SEEN_LIVE_MS = 120_000L
    private val HISTORY_READ_TRIES = 3

    /** Seen on a real pump: about 100 ms from one page to the next. */
    private val HISTORY_PAGE_TIMEOUT_MS = 5_000L

    /** The page the pump must send next. A page that did not come leaves a hole in the read. */
    @Volatile private var nextHistoryPage = 0

    /** When the read was asked or its last page came. A read that stops sending pages has lost one, maybe its last. */
    @Volatile private var lastHistoryPageAt = 0L

    /**
     * Set by a page that did not come or could not be written. The rest of that read is left out: the gap fill would take the
     * hole for time with no insulin. The same request brings all of it again. Used only by the history jobs.
     */
    @Volatile private var historyReadBroken = false

    /** Counts each new link and each loss. A read that ends after a reconnect belongs to the old link: the new one waits for its own. */
    @Volatile private var linkGeneration = 0

    /** Reads in a row with a hole. The start time of the next read stays the same, so a page that always fails would stop the history for good. */
    @Volatile private var historyReadFailures = 0
    @Volatile private var cancelTempBasalRunning = false
    @Volatile private var awaitingHistory = false
    @Volatile private var lastStatus: AaruyStatusSnapshot? = null

    /** The pump answered the bolus start with "bolus could not start". */
    @Volatile private var bolusStartRefused = false

    /** When a status last showed the pump giving insulin. A stop cannot have begun before it. */
    @Volatile private var lastDeliveringStatusAt = 0L

    /**
     * The status in the order of the history jobs. A history page whose job runs after a newer status came must
     * see the status of its own time: the newest could end a temp basal at a split piece of it.
     */
    @Volatile private var syncedStatus: AaruyStatusSnapshot? = null

    /** When [syncedStatus] came. */
    @Volatile private var syncedStatusAt = 0L

    /** Where the read asked last starts. A read the driver did not ask for has none, so it decides no unconfirmed bolus. */
    @Volatile private var historyReadFrom = Long.MAX_VALUE

    /** When the read asked last was asked. */
    @Volatile private var historyReadAskedAt = 0L

    /**
     * The unconfirmed bolus that a complete read covered: from before its start, asked after it would have ended. The
     * status that decides with it can come before or after it. Used only by the history jobs.
     */
    private var unconfirmedBolusLogRead: AaruyPump.UnconfirmedBolus? = null

    /** The pump clock can be some seconds behind the phone, and a start can reach the pump a moment after it was sent. */
    private val BOLUS_LOG_WINDOW_MS = 60_000L

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
    private var scanJob: Job? = null

    val aaBleUtil = AaruyBleClient(context, aapsLogger, transport)

    /** On the thread of the pump events, because it shares their state. */
    private val handler by lazy { Handler(aaBleUtil.eventLooper) }

    /** Set when the emulator replaces the radio, see AaruyBindings. */
    val emulatorTransport: AaruyEmulatorBleTransport? = transport as? AaruyEmulatorBleTransport
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    @Volatile private var sendBasalsInfoOk = false
    @Volatile private var basalWanted: BasalInfo? = null
    private var sendBolusOk = false
    private var cancelBolusOk = false
    @Volatile private var sendTmpBasalOk = false
    @Volatile private var cancelTmpBasalOk = false
    private var startBasalOk = false
    private var isUnbinding = false
    private var extendedAnnounced = false

    /** The driver rewinds an empty reservoir by itself, once. */
    private var emptyReservoirRewound = false

    var isConnected = false
    var isConnecting = false
    @Volatile var receivingHistory: Boolean = false

    /** Starts at the first connection: this class also exists for users of other pumps, and they need no check every 2 seconds. */
    private var detectionStarted = false

    private fun startDetection() {
        val detectionRunnable = object : Runnable {
            override fun run() {
                // Not scheduled again. The next connect starts it.
                if (stopped) {
                    detectionStarted = false
                    return
                }
                // No answer, or the last page never came. Also the first read after pairing, or history stays off.
                if (receivingHistory && isConnected && System.currentTimeMillis() - lastHistoryPageAt > HISTORY_PAGE_TIMEOUT_MS) {
                    aapsLogger.warn(LTag.PUMPCOMM, "History read stopped sending pages")
                    lastHistoryPageAt = System.currentTimeMillis()
                    syncJobs.trySend { onHistoryReadWithHole() }
                }

                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(detectionRunnable, 2000)
    }

    /** Only the command queue calls this, and it checks the Bluetooth permission first, for every pump. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(from: String, address: String?): Boolean {
        if (stopped) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "no connect, the driver is off: $from")
            return false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing Bluetooth ")
        if (bluetoothAdapter == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Unable to obtain a BluetoothAdapter.")
            return false
        }

        if (address == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "No pump address to connect to")
            return false
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Device not found. Unable to connect from: $from")
            return false
        }

        if (isConnected) {
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
            return true
        }

        aapsLogger.debug(LTag.PUMPBTCOMM, "Trying to create a new connection from: $from")
        isConnected = false
        isConnecting = true
        aaruyPump.connectionState = ConnectionState.CONNECTING
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
        // Last: if the link is still up the client reports CONNECTED during this call, and nothing may overwrite that.
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
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")

        if (isConnecting) {
            isConnecting = false
            stopScan()
            SystemClock.sleep(100)
        }

        if (bluetoothAdapter == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "disconnect is not possible: (mBluetoothAdapter == null) " + (bluetoothAdapter == null))
            isConnected = false
            return
        }

        // The command queue owns the link: it opens it for its commands and closes it when it has none left.
        aaBleUtil.closeLink()
        isConnected = false
        // The close runs on the pump thread. A connect asked at once could start before it and be closed by it.
        SystemClock.sleep(2000)
    }

    /**
     * The pump is told to forget this phone, and AAPS forgets the pump. Without a link only AAPS forgets it, and the pump
     * stays paired on its side. @return whether the pump was told
     */
    @Synchronized
    fun unbindDevice(): Boolean {
        val told = isConnected
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
        // The unbind went out by now. A pump out of reach would connect again as soon as it is back, with no pump paired.
        aaBleUtil.closeLink()
        return told
    }

    /** For the screens, which call on the main thread: [unbindDevice] waits for the pump for 2 s. Not stopped when the screen is left. */
    fun unbindDeviceInBackground() {
        scope.launch { unbindDevice() }
    }

    /** The plugin is off, as when the user chose another pump: this pump must not write into the database. */
    @Volatile private var stopped = false

    fun stop() {
        aapsLogger.info(LTag.PUMP, "Aaruy driver stopped")
        stopped = true
        // Only after a connect: for users of other pumps this would start the pump thread for nothing.
        if (detectionStarted) aaBleUtil.closeLink()
    }

    fun start() {
        stopped = false
    }

    /** Pumps found so far are in [scannedDevices], through the transport: the radio, or the emulator in its place. */
    fun startScan() {
        aapsLogger.info(LTag.PUMP, "startScan")
        _scannedDevices.value = emptyList()
        scanJob?.cancel()
        scanJob = scope.launch {
            // Started only when this listens, so no pump found in between is missed.
            transport.scanner.scannedDevices.onSubscription { transport.scanner.startScan() }.collect { device ->
                _scannedDevices.update { found -> if (found.any { it.address == device.address }) found else found + device }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        transport.scanner.stopScan()
    }

    /** A new pump: its old history is not read as new, and the connection that follows does the pairing. */
    fun pair(device: ScannedDevice) {
        aapsLogger.info(LTag.PUMP, "pair ${device.name}")
        preferences.put(AaruyStringKey.PumpBoundAddress, "")
        preferences.put(AaruyStringKey.MacAddress, device.address)
        preferences.put(AaruyStringKey.PumpName, device.name)
        // A link that is still up goes to the old pump. The wizard would take it for the new one.
        if (isConnected || isConnecting) aaBleUtil.closeLink()
        aaruyPump.canSaveHistory = false
        aaruyPump.lastHistoryTime = 0
        aaruyPump.lastDeliveryEnd = 0
        aaruyPump.lastHistoryDailyTime = dateUtil.now()
        // The last bolus stays: the user got it, also when the pump is a new one.
        rxBus.send(EventAaruyDeviceChange())
    }

    /**
     * The pump answers each alarm it is told about. Seen on a real pump: a blocked motor stopped beeping after this.
     * A command of the queue, which opens the link. @return false without a link: then nothing is sent.
     */
    fun confirmAlarms(): Boolean {
        // The pump would go on with the alarm, and AAPS would no longer show it.
        if (!isConnected) return false
        aaruyPump.activeAlarms.toList().forEach { clearAlarmStatus(it.state) }
        aaruyPump.clearAlarmState()
        return true
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        if (!detectionStarted) {
            detectionStarted = true
            startDetection()
        }
        aapsLogger.debug(LTag.PUMPCOMM, "connectDevice device = $device")
        listenerReceiveData()
        aaBleUtil.bindDevice(device)
    }

    /**
     * Starts the emulator if the file `emulate_aaruy` is in the AAPS extra folder, as for the other pump emulators.
     */
    fun startEmulatorIfActive() {
        val emulated = emulatorTransport ?: return
        // For tests: adb shell am broadcast -a app.aaps.pump.aaruy.emulator.OUT_OF_RANGE --ez value true
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val outOfRange = intent.getBooleanExtra("value", true)
                aapsLogger.warn(LTag.PUMPEMULATOR, "Aaruy emulator: out of range = $outOfRange")
                emulated.outOfRange = outOfRange
                if (outOfRange) emulated.loseConnection()
            }
        }, IntentFilter("app.aaps.pump.aaruy.emulator.OUT_OF_RANGE"), ContextCompat.RECEIVER_EXPORTED)
        // For tests: adb shell am broadcast -a app.aaps.pump.aaruy.emulator.SCENARIO --es value temp_basal_start --ei rate 500 --ei minutes 30
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val scenario = intent.getStringExtra("value")
                aapsLogger.warn(LTag.PUMPEMULATOR, "Aaruy emulator: scenario $scenario")
                emulated.onPumpThread {
                    val message = when (scenario) {
                        "lose_answer_temp_basal" -> { emulated.loseAnswerToParam = TEMP_BASAL_PARAM; null }
                        "lose_answer_bolus"      -> { emulated.loseAnswerToParam = BOLUS_CONTROL_PARAM; null }
                        "temp_basal_start"       -> emulated.emulator.startTempBasalByItself(intent.getIntExtra("rate", 500), intent.getIntExtra("minutes", 30))
                        "temp_basal_end"         -> emulated.emulator.endTempBasalByItself()
                        "stop"                   -> emulated.emulator.stopByItself()
                        "resume"                 -> emulated.emulator.resumeByItself()
                        "occlusion"              -> { emulated.emulator.blockMotor().forEach { emulated.pumpSends(it) }; null }
                        else                     -> null
                    }
                    message?.let { emulated.pumpSends(it) }
                }
            }
        }, IntentFilter("app.aaps.pump.aaruy.emulator.SCENARIO"), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun listenerReceiveData() {
        if (aaBleUtil.getOnBleListener() != null)
            return

        aapsLogger.debug(LTag.PUMPCOMM, "listenerReceiveData setOnBleListener")

        aaBleUtil.setOnBleListener(object : AaruyBleListener {
            override fun onBleStatusChanged(status: BleStatus) {
                aapsLogger.debug(LTag.PUMPCOMM, "aaruy onBleStatusChanged status = $status")
                if (status == BleStatus.CONNECTED && (stopped || preferences.get(AaruyStringKey.MacAddress) == "")) {
                    // Unpaired while the link came up, or the pump refused this phone: no pump of AAPS is on this link.
                    aapsLogger.warn(LTag.PUMPCOMM, "A link with no paired pump is closed, stopped: $stopped")
                    aaBleUtil.closeLink()
                } else if (status == BleStatus.CONNECTED) {
                    linkGeneration++
                    awaitingHistory = true
                    isConnected = true
                    isConnecting = false
                    isUnbinding = false
                    receivingHistory = false
                    stopScan()
                    val newPump = preferences.get(AaruyStringKey.PumpBoundAddress) == ""
                    // Before the name is taken: until then the name is a part of the address, not the serial number.
                    preferences.put(AaruyStringKey.PumpBoundAddress, preferences.get(AaruyStringKey.MacAddress))
                    if (newPump) {
                        // A new pump: the history screen shows where it started, with the changes of reservoir.
                        aapsLogger.debug(LTag.PUMPCOMM, "A new pump is paired")
                        val historyUnitInfo = HistoryUnitInfo()
                        historyUnitInfo.blueName = getShortDeviceName()
                        historyUnitInfo.type = AaruyHistoryTypes.NEW_PUMP.toInt()
                        historyUnitInfo.time = dateUtil.now()
                        historyUnitInfo.isUploaded = false
                        CoroutineScope(Dispatchers.IO).launch { historyUnitInfoDao.insert(historyUnitInfo) }
                    }
                    aaruyPump.connectionState = ConnectionState.CONNECTED
                    // Anything can have happened while the link was down, a bolus may have ended.
                    syncPumpStatus()
                    // The log can hold what the status does not show. And a reply that was lost must not leave history off.
                    requestHistoryData(aaruyPump.lastHistoryTime)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                } else if(status == BleStatus.DISCONNECTED) {
                    // A stop with no answer on this link says nothing about how a bolus ended while the link was down.
                    aaruyPump.bolusStopRequested = false
                    linkGeneration++
                    // A status before the next read is not live: the end of a temp basal seen then can be long ago.
                    awaitingHistory = true
                    // The end of this bolus can only be seen in the log now, whatever the next status says.
                    if (aaruyPump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING && !aaruyPump.bolusDone) aaruyPump.bolusLinkLost = true
                    isConnected = false
                    isConnecting = false
                    receivingHistory = false
                    aaruyPump.connectionState = ConnectionState.DISCONNECTED
                    if (isUnbinding) {
                        rxBus.send(EventAaruyDeviceChange())
                    }
                    // Not connected again here: the command queue connects for its next command, as for every pump.
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
                aapsLogger.debug(LTag.PUMPCOMM, "onRequestResult   $paramId($success)")
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
                aapsLogger.debug(LTag.PUMPCOMM, "onReceiveBasicParamSetting basicParam = $basicParam")
                // Taken, not written again. Seen on a real pump: while it delivers, it keeps its bolus step and answers each
                // write with the old one, so a write here started the next answer, for as long as the link was up. The
                // status read writes the settings once, and a change of a setting asks for a status read.
                aaruyPump.handleLoadBasicParam(basicParam)
            }

            override fun onReceiveBasalSetting(basalInfo: BasalInfo) {
                aapsLogger.debug(LTag.PUMPCOMM, "onReceiveBasalSetting basalInfo = $basalInfo")
                // The settings of a status read can still come, with the old profile. Only the new one is the answer.
                if (basalWanted?.let { aaruyPump.sameRates(it, basalInfo) } == true) sendBasalsInfoOk = true
                aaruyPump.actualBasalProfile = basalInfo
            }

            override fun onReceiveCurrentStatus(statusInfo: StatusInfo) {
                // Only an answer shows that the pump was reached.
                aaruyPump.lastConnection = dateUtil.now()
                onStatusForDatabase(statusInfo)
                val previousRate = aaruyPump.lastBasalRate
                val firstStatus = aaruyPump.pumpState == AaruyPumpState.FIRST_INIT
                aaruyPump.pumpState = AaruyPumpState.fromInt(statusInfo.runningStatus)
                // Screens that show what the pump can do wait for this.
                if (firstStatus) rxBus.send(EventInitializationChanged())
                aaruyPump.handleBasalStatusUpdate(0.0)

                // Before the branches below: some of them take any end of a bolus as one that was seen, with an old amount.
                if (aaruyPump.bolusLinkLost) {
                    // Still running: its end comes live, with the amount.
                    if (statusInfo.runningStatus == 3) aaruyPump.bolusLinkLost = false
                    else if (!aaruyPump.bolusDone) {
                        aaruyPump.bolusLinkLost = false
                        aaruyPump.handleBolusEndNotSeen()
                    }
                }

                when(statusInfo.runningStatus) {
                    0-> {       // Not running
                        checkPumpInfusion()
                    }
                    1-> {       // Basal running
                        startBasalOk = true
                        if (statusInfo.basalStatus?.curValue != previousRate) {
                            if (aaruyPump.canSaveHistory)
                                requestHistoryData(aaruyPump.lastHistoryTime)
                        }
                        aaruyPump.handleBasalStatusUpdate(statusInfo.basalStatus?.curValue ?: 0.0)
                    }
                    2->{        // Temp basal running
                        aaruyPump.handleBasalStatusUpdate(statusInfo.tempBasalStatus?.curValue ?: 0.0)
                    }
                    3->{        // Bolus running
                        if (statusInfo.bolusStatus?.state == BOLUS_STATE_EXTENDED) {
                            onExtendedRunning()
                        } else {
                            sendBolusOk = true
                            val bolusCompleted = statusInfo.bolusStatus?.state == BOLUS_STATE_DONE
                            if (bolusCompleted && aaruyPump.canSaveHistory) requestHistoryData(aaruyPump.lastHistoryTime)
                            val bolus = statusInfo.bolusStatus
                            if (!bolusCompleted && bolus != null) {
                                // Seen on a real pump: 0.05 U given and 18 s left was a bolus of 0.5 U, one step every 2 s.
                                val left = bolus.remainTime / BOLUS_SECONDS_PER_STEP * aaruyPump.bolusStepUnits
                                aaruyPump.showFoundBolus(total = bolus.curBolusNow / 1000 + left, isSmb = bolus.bolusType == BOLUS_TYPE_SMB)
                            }
                            aaruyPump.handleBolusStatusUpdate(bolusCompleted, (statusInfo.bolusStatus?.curBolusNow ?: 0.0) / 1000)
                        }
                    }
                    // 4: filling the cannula, 6: the piston looks for the reservoir. Nothing to do while they run.
                    5->{        // Rewind running
                        checkPumpInfusion()
                    }
                    7->{        // Paused, the piston stays where it is
                        if (aaruyPump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING) {
                            aaruyPump.handleBolusStatusUpdate(true)
                        }
                        if (aaruyPump.canSaveHistory)
                            requestHistoryData(aaruyPump.lastHistoryTime)
                        cancelBolusOk = true
                    }
                    8->{        // Stopped, after a rewind
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
                // Once. A status can come before the pump shows the rewind, so the flag. After it the pump stays stopped until the
                // reservoir is changed, which tells it also after an app restart.
                val rewound = aaruyPump.pumpState == AaruyPumpState.PUSHBACK_RUNNING || aaruyPump.pumpState == AaruyPumpState.STOP_RUNNING
                if (statusInfo.remainAmount > 0) emptyReservoirRewound = false
                else if (!rewound && !emptyReservoirRewound) {
                    emptyReservoirRewound = true
                    aaruyPump.warn(AlarmState.PUMP_ZERO_RESERVOIR)
                    replaceDevice()
                }
            }

            override fun onReceiveHistoryLog(logInfo: LogInfo) {
                aapsLogger.debug(LTag.PUMPCOMM, "onReceiveHistoryLog logInfo = $logInfo")
                onHistoryLog(logInfo)
            }

            override fun onReceiveHistoryDaily(dailyInfo: DailyInfo) {
                aapsLogger.debug(LTag.PUMPCOMM, "onReceiveHistoryDaily dailyInfo = $dailyInfo")
                dailyInfo.dailyItemList.lastOrNull()?.let { aaruyPump.lastHistoryDailyTime = it.timestamp }
                saveDailyData(dailyInfo)
            }

            override fun onReceiveRealData(battery: Int, signal: Byte) {
                aapsLogger.debug(LTag.PUMPCOMM, "onReceiveRealData battery = $battery,signal = $signal")
                aaruyPump.remainBattery = battery.toDouble()
            }

            override fun onReceiveErrorData(errorType: Int, error: Int) {
                aapsLogger.debug(LTag.PUMPCOMM, "onReceiveErrorData errorType = $errorType,error = $error")
                val tmpErrCode = (error shl 8) or (errorType)
                aaruyPump.errCode.tryEmit(tmpErrCode)
                // High byte 0 is the answer to a motor command. The wizard reads it from errCode, nothing else needs it.
                when(error) {
                    0x80->{     // An error answer to a command. A refused history request is asked again when no page comes.
                        if (tmpErrCode == AlarmState.PUMP_RUN_BOLUS.state) bolusStartRefused = true
                        // Each answer is new: the command it refuses was sent again.
                        val alarmState = AlarmState.fromInt(tmpErrCode)
                        if (alarmState != AlarmState.NONE) aaruyPump.warn(alarmState)
                    }
                    0x81->{     // An alarm. Seen on a real pump: a blocked motor repeats it until it is confirmed.
                        val alarmState = AlarmState.fromInt(tmpErrCode)
                        if (alarmState != AlarmState.NONE) {
                            if (AlarmState.isHighAlarm(alarmState) || AlarmState.isLowAlarm(alarmState)) aaruyPump.addAlarm(alarmState)
                            aaruyPump.warnOnce(alarmState)
                        }
                        if (aaruyPump.canSaveHistory)
                            requestHistoryData(aaruyPump.lastHistoryTime)
                    }
                }
            }

            override fun onReceiveConfirmWarn(confirm: Int) {
                aapsLogger.debug(LTag.PUMPCOMM, "onReceiveConfirmWarn confirm = $confirm")
            }

            override fun onUnbindClearInfo() {
                aapsLogger.debug(LTag.PUMPCOMM, "onUnbindClearInfo")
                // The pump refused this phone. Without a word the pairing would just stop, and the wizard would wait.
                notificationManager.post(NotificationId.DEVICE_NOT_PAIRED, rh.gs(R.string.aaruy_pump_bound_elsewhere))
                preferences.put(AaruyStringKey.PumpBoundAddress, "")
                preferences.put(AaruyStringKey.MacAddress, "")
                preferences.put(AaruyStringKey.PumpName, "")
            }

        })
    }

    internal fun onHistoryLog(logInfo: LogInfo) {
        lastHistoryPageAt = System.currentTimeMillis()
        // A read starts at page 0, also one the driver did not ask for.
        val pageMissing = logInfo.curPage != 0 && logInfo.curPage != nextHistoryPage
        if (pageMissing) aapsLogger.warn(LTag.PUMPCOMM, "History page ${logInfo.curPage} came, page $nextHistoryPage was expected")
        nextHistoryPage = logInfo.curPage + 1
        // A real pump has no history after pairing: one empty page, and the total is 0.
        val lastPage = logInfo.curPage + 1 >= logInfo.totalPage
        aapsLogger.debug(LTag.PUMPCOMM, "History page ${logInfo.curPage} of ${logInfo.totalPage}, saved: ${aaruyPump.canSaveHistory}")
        if (aaruyPump.canSaveHistory) saveHistoryData(logInfo, pageMissing, lastPage)
        else if (lastPage) {
            // The first read after pairing only finds where the log ends. Nothing is lost if a page is missing here.
            logInfo.logItems.lastOrNull()?.let { aaruyPump.lastHistoryTime = it.timestamp }
            aaruyPump.canSaveHistory = true
            receivingHistory = false
            readDailyData(aaruyPump.lastHistoryDailyTime)
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

    /**
     * The highest id in the stored pump log when the last bolus start was sent. The entry of that bolus can only be stored
     * after it: a row up to it is an earlier bolus, also one within a minute of this one.
     */
    @Volatile var logIdBeforeStart = 0
        private set

    fun setBolus(insulin: Double, isSmb: Boolean = true): AaruyBolusStart {
        sendBolusOk = false
        bolusStartRefused = false
        logIdBeforeStart = historyUnitInfoDao.maxId()
        aaBleUtil.setupAndStartBolus(insulin, 0.0, 0, isSmb)
        // A status that comes late must not report a failure while the pump delivers: the user could bolus again.
        val end = System.currentTimeMillis() + BOLUS_START_TIMEOUT_MS
        while (!sendBolusOk && !bolusStartRefused && System.currentTimeMillis() < end) SystemClock.sleep(100)
        // Asked even after a status 3: the start must not go out later from the queue.
        val startSent = aaBleUtil.abandonBolusStart()
        return when {
            sendBolusOk       -> AaruyBolusStart.STARTED
            // Seen on a real pump: set to 0.05 U it refused 0.125 U. That is an answer, and no insulin goes in.
            bolusStartRefused -> AaruyBolusStart.NOT_SENT
            startSent         -> AaruyBolusStart.NO_ANSWER
            else              -> AaruyBolusStart.NOT_SENT
        }
    }

    fun cancelBolus(): Boolean {
        cancelBolusOk = false
        aaBleUtil.cancelBolus()
        val end = System.currentTimeMillis() + BOLUS_STOP_TIMEOUT_MS
        while (!cancelBolusOk && System.currentTimeMillis() < end) SystemClock.sleep(20)
        // No CONTINUE after it. Seen on a real pump: it goes back to basal or the temp basal by itself. CONTINUE would
        // restart a pump that a blocked motor or an empty reservoir had stopped while the stop was on its way.
        return cancelBolusOk
    }

    fun setTempBasal(absoluteRate: Double, durationInMinutes: Int): Boolean {
        sendTmpBasalOk = false
        aaBleUtil.setClosedMode(1)
        SystemClock.sleep(200)
        aaBleUtil.startTempBasal(absoluteRate, durationInMinutes)
        return waitForAnswer { sendTmpBasalOk } || giveUpOnTempBasal { sendTmpBasalOk }
    }

    fun cancelTempBasal(): Boolean {
        cancelTmpBasalOk = false
        cancelTempBasalRunning = true
        aaBleUtil.cancelTempBasal()
        val answered = waitForAnswer { cancelTmpBasalOk } || giveUpOnTempBasal { cancelTmpBasalOk }
        cancelTempBasalRunning = false
        return answered
    }

    /**
     * No answer in time: the command is reported as failed, so it must not run later. A frame the pump's count shows lost
     * is sent again, and it once ran 0.4 s after the driver had given up (CI under load). One sent again just before
     * this still gets its answer within a moment.
     */
    private fun giveUpOnTempBasal(answered: () -> Boolean): Boolean {
        aaBleUtil.abandonTempBasal()
        val end = System.currentTimeMillis() + ANSWER_AFTER_GIVING_UP_MS
        while (!answered() && System.currentTimeMillis() < end) SystemClock.sleep(50)
        return answered()
    }

    /** After a reconnect the loop must not decide on what the driver knew before the log was read. */
    fun waitForHistoryAfterReconnect() {
        val end = System.currentTimeMillis() + HISTORY_WAIT_MS
        while (awaitingHistory && System.currentTimeMillis() < end) SystemClock.sleep(50)
    }

    /** The flags that end a waiting command come after the write, so the command finds the result in the database. */
    internal fun onStatusForDatabase(statusInfo: StatusInfo) {
        val snapshot = AaruyStatusSnapshot(
            status = statusInfo.runningStatus,
            rate = statusInfo.tempBasalStatus?.curValue ?: 0.0,
            remainingSeconds = statusInfo.tempBasalStatus?.remainTime ?: 0,
            runningSeconds = statusInfo.tempBasalStatus?.runningTime ?: 0
        )
        lastStatus = snapshot
        if (snapshot.status in STATUS_BASAL..STATUS_BOLUS) lastDeliveringStatusAt = dateUtil.now()
        val live = !awaitingHistory || cancelTempBasalRunning
        syncJobs.trySend {
            syncedStatus = snapshot
            syncedStatusAt = dateUtil.now()
            // The pump runs a bolus, so it had the start: the log gives its amount.
            if (snapshot.status == STATUS_BOLUS) aaruyPump.unconfirmedBolus = null
            else decideUnconfirmedBolus()
            tempBasalSync.onStatus(snapshot, live)
            if (snapshot.status == STATUS_TEMP_BASAL) sendTmpBasalOk = true
            else if (snapshot.status != STATUS_BOLUS) cancelTmpBasalOk = true
            statusesWritten++
        }
    }

    /** How many statuses were written to the database, counted at the end of their job. See [readStatusAndWait]. */
    @Volatile private var statusesWritten = 0

    /**
     * Asks for the status and waits until it is in the database, so the queue's status read ends with AAPS knowing what
     * the pump does, as the other drivers do. Without the wait the read ended before the answer (about 230 ms on a real
     * pump), and a lost temp basal answer was still missing in AAPS right after it. Only from the queue's thread: the
     * answer comes on the pump thread. False when no answer came in time.
     */
    fun readStatusAndWait(): Boolean {
        // A status the pump pushes by itself in the meantime is as new as the answer, so it counts too.
        val before = statusesWritten
        aaBleUtil.getCurStatus()
        return waitForAnswer { statusesWritten > before }
    }

    /** The log is read. What the status could not decide before can be decided now. */
    private fun onHistoryRead() {
        val generation = linkGeneration
        syncJobs.trySend { if (generation == linkGeneration) historyRead() }
    }

    /**
     * Runs as a history job, after the writes of the read. The newest status, also one whose own job comes later: it ran with live false.
     * @param complete the log is read to its end, so where it ends is where the pump last gave insulin
     */
    private suspend fun historyRead(complete: Boolean = false) {
        awaitingHistory = false
        // A segment is logged when it ends, and the stop ends it: a stop during the read is not in it. A status that showed delivery is later proof.
        val stoppedSince = if (complete) aaruyPump.lastDeliveryEnd.takeIf { it > 0 }?.let { max(it, lastDeliveringStatusAt) } else null
        lastStatus?.let { tempBasalSync.onStatus(it, live = true, stoppedSince = stoppedSince) }
    }

    /** Writes the user settings. What the pump holds is taken from its answer, not from this write. */
    fun syncAndLoadBasicParam() {
        aaBleUtil.setupBasicParamInfo(aaruyPump.settingsForPump())
    }

    /**
     * Writes the user settings only when the pump lacks some and takes them now, see [AaruyPump.settingsToWrite]. With a
     * loop a temp basal runs nearly all the time, so the moment after a cancel is the one that counts. The state comes
     * from the last status, which is set before its database job ends the wait of a cancel.
     */
    fun writeSettingsIfNeeded() {
        val state = lastStatus?.let { AaruyPumpState.fromInt(it.status) } ?: aaruyPump.pumpState
        if (aaruyPump.settingsToWrite(state)) syncAndLoadBasicParam()
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

    /** See [AaruyBleClient.allowRestoreAfterRewind]: the pump log shows the rewind, also one from before an app restart. */
    fun allowRestoreAfterRewind() {
        aaBleUtil.allowRestoreAfterRewind()
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
        val from = historyReadStart(lastTime)
        aapsLogger.debug(LTag.PUMPCOMM, "requestHistoryData lastTime = $lastTime, from = $from, receivingHistory is $receivingHistory")
        if (!receivingHistory) {
            nextHistoryPage = 0
            lastHistoryPageAt = System.currentTimeMillis()
            historyReadFrom = from
            historyReadAskedAt = dateUtil.now()
            receivingHistory = true
            CoroutineScope(Dispatchers.Default).launch {
                delay(20)
                aaBleUtil.getHistoryLogData(from)
            }
        }
    }

    /**
     * Once a bolus whose start no status answered would have ended, the read goes back to before its start. A read from the
     * last entry can miss it: the pump logs a bolus at its end with its start time, and an entry logged before it, as an
     * alarm, can be newer.
     */
    private fun historyReadStart(lastTime: Long): Long {
        val unconfirmed = aaruyPump.unconfirmedBolus ?: return lastTime
        if (dateUtil.now() < unconfirmed.decideAfter) return lastTime
        return min(lastTime, unconfirmed.start - BOLUS_LOG_WINDOW_MS)
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
            name = preferences.get(AaruyStringKey.MacAddress)         // The pump that is being connected
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

    private fun saveHistoryData(logInfo: LogInfo, pageMissing: Boolean, lastPage: Boolean) {
        val generation = linkGeneration
        syncJobs.trySend {
            if (logInfo.curPage == 0) {
                historyReadBroken = false
                // A read that did not end is read again from its start.
                tempBasalWaitingForNextPage = null
            }
            if (pageMissing) historyReadBroken = true
            if (historyReadBroken) aapsLogger.warn(LTag.PUMPCOMM, "History page ${logInfo.curPage} left out: a page before it is missing")
            else try {
                saveHistoryPage(logInfo, lastPage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The entries after the one that failed are not written. The same request brings them again.
                aapsLogger.error(LTag.PUMPCOMM, "History page ${logInfo.curPage} could not be written", e)
                historyReadBroken = true
            }
            if (lastPage) {
                // What it wrote is good. The new link reads from the same time again.
                if (generation != linkGeneration) aapsLogger.debug(LTag.PUMPCOMM, "A history read of an earlier link ended")
                else if (historyReadBroken) onHistoryReadWithHole()
                else onHistoryReadComplete(logInfo)
                tempBasalWaitingForNextPage = null
            }
        }
    }

    /**
     * A temp basal entry with nothing after it on its page that tells if it went on: the next page tells. With its next start
     * when its page had one. Only in the history jobs.
     */
    private var tempBasalWaitingForNextPage: Pair<LogItem, Long?>? = null

    /** Only now: an entry not yet written must come again if the app stops here. */
    private suspend fun onHistoryReadComplete(lastPage: LogInfo) {
        val readFrom = historyReadFrom
        val readAskedAt = historyReadAskedAt
        historyReadFrom = Long.MAX_VALUE
        // Nothing came after it: it is the newest, and only the status can tell.
        tempBasalWaitingForNextPage?.let { (waiting, nextStart) -> tempBasalSync.onHistoryTempBasal(waiting, nextStart, null, syncedStatus?.status) }
        lastPage.logItems.lastOrNull()?.let { aaruyPump.lastHistoryTime = it.timestamp }
        historyReadFailures = 0
        receivingHistory = false
        readDailyData(aaruyPump.lastHistoryDailyTime)
        historyRead(complete = true)
        aaruyPump.unconfirmedBolus?.let { unconfirmed ->
            val covers = readFrom <= unconfirmed.start - BOLUS_LOG_WINDOW_MS && readAskedAt >= unconfirmed.decideAfter
            aapsLogger.debug(LTag.PUMPCOMM, "Unconfirmed bolus start ${unconfirmed.start}: read from $readFrom asked at $readAskedAt, decide after ${unconfirmed.decideAfter}, covers it: $covers")
            if (covers) unconfirmedBolusLogRead = unconfirmed
        }
        decideUnconfirmedBolus()
    }

    /**
     * A bolus start that no status answered, decided by the pump log. The pump logs a bolus when it ends, so the log can tell
     * only when a bolus started then has surely ended: a complete read went back to before the start and was asked once the
     * bolus would have ended, and a status since then shows no bolus. A pump that still gives it sends status 3, which ends
     * the question. A bolus entry at that time has already set the record to what the pump gave. Without one the pump never
     * gave it, and the record that counted the whole amount becomes 0 U. Runs as a history job, after the read and after
     * each status, as the two can come in either order.
     */
    private suspend fun decideUnconfirmedBolus() {
        val unconfirmed = aaruyPump.unconfirmedBolus ?: return
        if (unconfirmedBolusLogRead !== unconfirmed) return
        if (syncedStatusAt < unconfirmed.decideAfter || syncedStatus?.status == STATUS_BOLUS) {
            aapsLogger.debug(LTag.PUMPCOMM, "Unconfirmed bolus start ${unconfirmed.start}: the log is read, a status after ${unconfirmed.decideAfter} is missing (last $syncedStatusAt, ${syncedStatus?.status})")
            return
        }
        aaruyPump.unconfirmedBolus = null
        unconfirmedBolusLogRead = null
        val logged = historyUnitInfoDao.querySince(unconfirmed.start - BOLUS_LOG_WINDOW_MS)
            .any { it.id > unconfirmed.logIdBefore && it.type == HISTORY_BOLUS && it.delayBolus == 0L && it.time <= unconfirmed.start + BOLUS_LOG_WINDOW_MS }
        if (logged) return
        aapsLogger.warn(LTag.PUMPCOMM, "No bolus in the pump log at ${dateUtil.dateAndTimeAndSecondsString(unconfirmed.start)}: the pump never gave it")
        val info = detailedBolusInfoStorage.findDetailedBolusInfo(unconfirmed.start, unconfirmed.amount)
        val changed = pumpSync.syncBolusWithTempId(
            timestamp = unconfirmed.start,
            amount = PumpInsulin(0.0),
            temporaryId = unconfirmed.start,
            type = info?.bolusType,
            pumpId = unconfirmed.start,
            pumpType = aaruyPump.pumpType(),
            pumpSerial = aaruyPump.pumpSN
        )
        if (changed) pumpSync.insertAnnouncement(rh.gs(R.string.aaruy_bolus_not_given, dateUtil.timeString(unconfirmed.start)), dateUtil.now(), aaruyPump.pumpType(), aaruyPump.pumpSN)
    }

    private suspend fun saveHistoryPage(logInfo: LogInfo, lastPage: Boolean) {
        // Its page ended with it, so the first entry of this page is the exact end.
        tempBasalWaitingForNextPage = tempBasalWaitingForNextPage?.let { (waiting, nextStart) -> waiting to (nextStart ?: logInfo.logItems.firstOrNull()?.timestamp) }
        logInfo.logItems.forEachIndexed { index, item ->
            // First the temp basal of the page before, which this entry ends or goes on with. A new one would cut its record.
            if (item.tellsIfATempBasalWentOn()) tempBasalWaitingForNextPage?.let { (waiting, nextStart) ->
                tempBasalWaitingForNextPage = null
                tempBasalSync.onHistoryTempBasal(waiting, nextStart, item, syncedStatus?.status)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "saveHistoryData item.data = ${item.data}, item.delayBolus is ${item.delayBolus}")
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

            // Written only after its sync, so a known entry was synced. Again, a sync with no type changed a PRIMING record by the concentration.
            val known = historyUnitInfoDao.count(item.timestamp, historyUnitInfo.type, historyUnitInfo.reserved) > 0
            if (item.type == 2 && item.delayBolus.toInt() == 0 && !known) {
                // A read can bring old entries again. The minute is for the pump clock, which can be some seconds behind the phone.
                if (item.timestamp > aaruyPump.lastBolusTime - T.mins(1).msecs()) {
                    aaruyPump.lastBolusAmount = item.data / 1000.0
                    aaruyPump.lastBolusTime = item.timestamp
                }

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
                        // No record with this temporary id, as when the user deleted it. The pump gave this bolus, so it is written all the same.
                        aapsLogger.warn(LTag.PUMPCOMM, "saveHistoryData: BOLUS_RECORD: no record with tempId ${detailedBolusInfo.timestamp}, written by pump id")
                        newRecord = pumpSync.syncBolusWithPumpId(
                            timestamp = item.timestamp,
                            amount = PumpInsulin(item.data / 1000.0),
                            type = detailedBolusInfo.bolusType,
                            pumpId = item.timestamp,
                            pumpType = aaruyPump.pumpType(),
                            pumpSerial = aaruyPump.pumpSN
                        )
                    }
                    else {
                        aapsLogger.debug(
                            LTag.PUMPCOMM,
                            "EVENT syncBolusWithTempId ok timestamp ${dateUtil.dateAndTimeString(aaruyPump.lastBolusTime)} Bolus: ${aaruyPump.lastBolusAmount}U "
                        )
                    }
                } else {
                    // The stored info is gone, as after an app restart: the store keeps only the last two. The start of
                    // the last bolus is saved, and it is the temporary id of its record. Without this the bolus was
                    // written a second time, next to its record with the amount asked for.
                    val savedStart = aaruyPump.bolusStartTime
                    val lastBolusOfThisPhone = abs(item.timestamp - savedStart) < T.mins(1).msecs() && item.data / 1000.0 <= aaruyPump.bolusAmountToBeDelivered + 0.01
                    val syncOk = lastBolusOfThisPhone && pumpSync.syncBolusWithTempId(
                        timestamp = item.timestamp,
                        amount = PumpInsulin(item.data / 1000.0),
                        temporaryId = savedStart,
                        type = null,
                        pumpId = item.timestamp,
                        pumpType = aaruyPump.pumpType(),
                        pumpSerial = aaruyPump.pumpSN
                    )
                    if (!syncOk) newRecord = pumpSync.syncBolusWithPumpId(
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
            val next = logInfo.logItems.drop(index + 1).firstOrNull { it.tellsIfATempBasalWentOn() }
            syncFromHistory(item, logInfo.logItems.getOrNull(index + 1)?.timestamp, next, lastPage)
            if (!known) historyUnitInfoDao.insert(historyUnitInfo)
        }
    }

    /**
     * The pages before the hole stay stored. The request has no page number, only a start time, so the same
     * request goes again. Entries that come twice are not stored twice.
     */
    private suspend fun onHistoryReadWithHole() {
        historyReadFailures++
        aapsLogger.warn(LTag.PUMPCOMM, "History read with a missing page, $historyReadFailures in a row. The log time stays at ${aaruyPump.lastHistoryTime}")
        receivingHistory = false
        if (historyReadFailures < HISTORY_READ_TRIES) requestHistoryData(aaruyPump.lastHistoryTime)
        else {
            if (historyReadFailures == HISTORY_READ_TRIES)
                notificationManager.post(NotificationId.PUMP_SYNC_ERROR, rh.gs(R.string.aaruy_history_read_failed), validityCheck = { historyReadFailures >= HISTORY_READ_TRIES })
            // Waiting longer would keep every status from ending a temp basal for as long as the link is up.
            historyRead()
        }
    }

    /**
     * What the pump log knows and the driver may not: all of this can happen while the link is down.
     * @param next the next entry of this page that tells if a temp basal went on
     */
    private suspend fun syncFromHistory(item: LogItem, nextStart: Long?, next: LogItem?, lastPage: Boolean) {
        // An entry counts whole minutes, so the middle of the last minute is at most 30 seconds off. Without this a gap comes out too long.
        val minutes = T.mins(item.injectTime.toLong()).msecs() + T.secs(30).msecs()
        when (item.type.toInt()) {
            HISTORY_BASAL      -> tempBasalSync.onHistoryDelivery(item.timestamp, item.timestamp + minutes)

            HISTORY_TEMP_BASAL -> {
                tempBasalSync.onHistoryDelivery(item.timestamp, item.timestamp + minutes)
                // The next page tells. Only the newest entry of the whole read is left to the status.
                if (next == null && !lastPage) tempBasalWaitingForNextPage = item to nextStart
                else tempBasalSync.onHistoryTempBasal(item, nextStart, next, syncedStatus?.status)
            }

            HISTORY_BOLUS      -> if (item.delayBolus.toInt() == 0) {
                // Seen on a real pump: no basal entry runs during a bolus, so this time is no gap. One step every 2 seconds.
                val steps = item.data.toLong() / (aaruyPump.bolusStep * AaruySetting.precision)
                tempBasalSync.onHistoryDelivery(item.timestamp, item.timestamp + T.secs(steps * 2).msecs())
            }

            HISTORY_ALARM      -> {
                // A newer one was announced when it came. Only one from the time without a link is new.
                if (dateUtil.now() - item.timestamp < ALARM_SEEN_LIVE_MS) return
                val alarm = AlarmState.fromHistory(item.data.toInt())
                // At the time of the alarm, and once: a read brings its last entry again, and a read with a hole is asked again.
                if (alarm != AlarmState.NONE)
                    pumpSync.insertTherapyEventIfNewWithTimestamp(item.timestamp, TE.Type.ANNOUNCEMENT, aaruyPump.alarmStateToString(alarm), item.timestamp, aaruyPump.pumpType(), aaruyPump.pumpSN)
            }
        }
    }

    internal fun saveDailyData(dailyInfo: DailyInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentPumpName = getShortDeviceName()
            for (item in dailyInfo.dailyItemList) {
                aapsLogger.debug(LTag.PUMPCOMM, "saveDailyData item.dailyBolus = ${item.dailyBolus}, item.dailyTotal is ${item.dailyTotal}")
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