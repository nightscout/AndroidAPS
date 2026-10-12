package app.aaps.pump.equil.manager

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.waitMillis
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.EquilBLE
import app.aaps.pump.equil.data.BolusProfile
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.BolusType
import app.aaps.pump.equil.database.EquilBasalValuesRecord
import app.aaps.pump.equil.database.EquilBolusRecord
import app.aaps.pump.equil.database.EquilHistoryPump
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.database.EquilTempBasalRecord
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState
import app.aaps.pump.equil.events.EventEquilAlarm
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.events.EventEquilInsulinChanged
import app.aaps.pump.equil.events.EventEquilModeChanged
import app.aaps.pump.equil.events.EventEquilResumed
import app.aaps.pump.equil.keys.EquilIntPreferenceKey
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.Utils.bytesToInt
import app.aaps.pump.equil.manager.Utils.internalDecodeSpeedToUH
import app.aaps.pump.equil.manager.command.BaseCmd
import app.aaps.pump.equil.manager.command.CmdBasalSet
import app.aaps.pump.equil.manager.command.CmdExtendedBolusSet
import app.aaps.pump.equil.manager.command.CmdHistoryGet
import app.aaps.pump.equil.manager.command.CmdLargeBasalSet
import app.aaps.pump.equil.manager.command.CmdRunningModeGet
import app.aaps.pump.equil.manager.command.CmdTempBasalGet
import app.aaps.pump.equil.manager.command.CmdTempBasalSet
import app.aaps.pump.equil.manager.command.PumpEvent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import java.util.Calendar
import java.util.Optional
import kotlin.math.ceil
import kotlin.math.min

@SingleIn(AppScope::class)
@Inject
class EquilManager(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val pumpSync: PumpSync,
    private val equilBLE: EquilBLE,
    private val equilHistoryRecordDao: EquilHistoryRecordDao,
    private val equilHistoryPumpDao: EquilHistoryPumpDao,
    private val pumpEnactResultProvider: () -> PumpEnactResult,
    private val dateUtil: DateUtil,
    private val ch: ConcentrationHelper,
    private val bolusProgressData: BolusProgressData,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val gsonInstance: Gson = createGson()
    private val runModeSyncMutex = Mutex()
    var equilState: EquilState? = null
        private set

    /** Guards the one-time read in [init]. */
    private var podStateLoaded = false

    val lastConnectionFlow = MutableStateFlow(0L)
    val lastBolusTimeFlow = MutableStateFlow<Long?>(null)
    val lastBolusAmountFlow = MutableStateFlow<Double?>(null)
    val reservoirFlow = MutableStateFlow(0.0)
    val batteryFlow = MutableStateFlow<Int?>(null)

    /**
     * Called from `EquilPumpPlugin.onStart`, which can run more than once per process - the plugin is
     * started again whenever it is re-enabled.
     *
     * The pod state is only read from preferences the **first** time. Every change to [equilState] is
     * written through to preferences immediately, so memory is never older than disk: a second load can
     * only replace newer in-memory state with whatever happens to be persisted. That is not theoretical
     * - `onStart` runs on a background coroutine, so a re-enable landing during pod activation reset an
     * activation that was already in progress, and the wizard then waited forever for a COMPLETED it had
     * already reached (#5040).
     *
     * [equilBLE] still initialises on every start; only the read is once.
     */
    fun init() {
        if (!podStateLoaded) {
            loadPodState()
            podStateLoaded = true
        }
        equilBLE.init(this)
    }

    fun getEquilError(port: Int, type: Int, level: Int): String =
        PumpEvent.getErrorString(rh, port, type, level)

    fun closeBleAuto() {
        equilBLE.closeBleAuto()
    }

    fun connect() {
        equilBLE.connect("EquilManager::connect")
    }

    fun getTempBasalPump(): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command = CmdTempBasalGet(aapsLogger, preferences, this)
            equilBLE.writeCmd(command)
            synchronized(command) {
                command.waitMillis(command.timeOut.toLong())
            }
            result.success = command.cmdSuccess
            result.enacted(command.time != 0)
            SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    suspend fun setTempBasal(insulin: Double, time: Int, cancel: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command = CmdTempBasalSet(insulin, time, aapsLogger, preferences, this)
            command.cancel = cancel
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                command.waitMillis(command.timeOut.toLong())
            }
            if (command.cmdSuccess) {
                val currentTime = System.currentTimeMillis()
                if (cancel) {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        currentTime,
                        currentTime,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                    setTempBasal(null)
                } else {
                    val tempBasalRecord =
                        EquilTempBasalRecord(
                            time * 60 * 1000,
                            insulin, currentTime
                        )
                    setTempBasal(tempBasalRecord)
                    pumpSync.syncTemporaryBasalWithPumpId(
                        currentTime,
                        PumpRate(insulin),
                        time.toLong() * 60 * 1000,
                        true,
                        PumpSync.TemporaryBasalType.NORMAL,
                        currentTime,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                }
                command.resolvedResult = ResolvedResult.SUCCESS
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            loadEquilHistory()
            result.success = command.cmdSuccess
            result.enacted(command.cmdSuccess)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    suspend fun setExtendedBolus(insulin: Double, time: Int, cancel: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command = CmdExtendedBolusSet(insulin, time, cancel, aapsLogger, preferences, this)
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                command.waitMillis(command.timeOut.toLong())
            }

            result.success = command.cmdSuccess
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
                val currentTimeMillis = System.currentTimeMillis()
                if (cancel) {
                    pumpSync.syncStopExtendedBolusWithPumpId(
                        currentTimeMillis,
                        currentTimeMillis,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                } else {
                    pumpSync.syncExtendedBolusWithPumpId(
                        currentTimeMillis,
                        PumpRate(insulin),
                        time.toLong() * 60 * 1000,
                        true,
                        currentTimeMillis,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                }

                result.enacted(true)
            } else {
                result.success = false
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            loadEquilHistory()
        } catch (ex: Exception) {
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    suspend fun bolus(detailedBolusInfo: DetailedBolusInfo, bolusProfile: BolusProfile): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            // Clamped to the tested range: a broken value must never send a bolus faster than 12 U/min.
            val speedKey = EquilIntPreferenceKey.EquilBolusSpeed
            val secondsPerUnit = preferences.get(speedKey).coerceIn(speedKey.min, speedKey.max)
            val command = CmdLargeBasalSet(detailedBolusInfo.insulin, aapsLogger, preferences, this, secondsPerUnit)
            val equilHistoryRecord = addHistory(command)
            lastDeliveryStoppedAt = null
            equilBLE.writeCmd(command)
            synchronized(command) {
                command.waitMillis(command.timeOut.toLong())
            }
            val bolusStart = System.currentTimeMillis()
            bolusProfile.stop = false
            val sleep = 2000
            // The pump gives the bolus in command.stepTime seconds; the progress follows that time.
            val percent1 = 100f * (sleep / 1000f) / command.stepTime
            aapsLogger.debug(LTag.PUMPCOMM, "sleep===" + detailedBolusInfo.insulin + "===" + percent1)
            var percent = 0f
            if (command.cmdSuccess) {
                result.success = true
                result.enacted(true)
                while (!bolusProfile.stop && percent < 100) {
                    bolusProgressData.updateProgress(percent.toInt())
                    SystemClock.sleep(sleep.toLong())
                    percent += percent1
                    aapsLogger.debug(LTag.PUMPCOMM, "isCmdStatus===" + percent + "====" + bolusProfile.stop)
                }
                // constraint percent.
                percent = min(percent, 100.0f)
                bolusProgressData.updateProgress(percent = 100)
                result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
            } else {
                result.success = false
                result.enacted(false)
                result.comment = rh.gs(R.string.equil_command_connect_error)
            }
            result.bolusDelivered = percent / 100.0 * detailedBolusInfo.insulin
            val currentTime = System.currentTimeMillis()
            if (result.success) {
                command.resolvedResult = ResolvedResult.SUCCESS
                pumpSync.syncBolusWithPumpId(
                    currentTime,
                    PumpInsulin(result.bolusDelivered),
                    detailedBolusInfo.bolusType,
                    detailedBolusInfo.timestamp,
                    PumpType.EQUIL,
                    getSerialNumber()
                )
                val equilBolusRecord = EquilBolusRecord(result.bolusDelivered, BolusType.SMB, currentTime)
                setBolusRecord(equilBolusRecord)
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            // The pump closes the link about 10 s after the bolus command, so after a longer bolus there is
            // no link left. Then connect again and read the run mode and history now: otherwise a pump alarm
            // during the bolus (e.g. empty reservoir) was only seen at the next loop run (#3467).
            if (equilBLE.isConnected) loadEquilHistory() else readModeAndHistory()
            if (result.success) correctBolusIfDeliveryStopped(result, detailedBolusInfo, bolusStart, currentTime, detailedBolusInfo.insulin / command.stepTime)
        } catch (ex: Exception) {
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    /**
     * The progress above runs from a timer, so the full amount was recorded even if the reservoir ran
     * empty or the pump stopped it during the bolus. The pump then stops and rewinds (#3467). If the
     * history read right after the bolus has such a record, record only what was given until then:
     * [unitsPerSecond] (the speed this bolus was sent with) x time. It is rounded up to the 0.05 U step,
     * so AAPS never counts less insulin than was given.
     */
    @VisibleForTesting
    internal suspend fun correctBolusIfDeliveryStopped(result: PumpEnactResult, detailedBolusInfo: DetailedBolusInfo, bolusStart: Long, bolusTime: Long, unitsPerSecond: Double) {
        val stoppedAt = lastDeliveryStoppedAt ?: return
        // Pump time has whole seconds and may be a little off; an older stop is not about this bolus.
        if (stoppedAt < bolusStart - T.secs(10).msecs()) return
        val requested = result.bolusDelivered
        val units = (stoppedAt - bolusStart).coerceAtLeast(0) / 1000.0 * unitsPerSecond
        val given = ceil(units * BOLUS_STEPS_PER_UNIT - 1e-9) / BOLUS_STEPS_PER_UNIT
        if (given >= requested) return
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery stopped during the bolus: $given of $requested U")
        result.bolusDelivered = given
        pumpSync.syncBolusWithPumpId(bolusTime, PumpInsulin(given), detailedBolusInfo.bolusType, detailedBolusInfo.timestamp, PumpType.EQUIL, getSerialNumber())
        setBolusRecord(EquilBolusRecord(given, BolusType.SMB, bolusTime))
        val text = rh.gs(R.string.equil_bolus_stopped, ch.insulinAmountString(PumpInsulin(given)), ch.insulinAmountString(PumpInsulin(requested)))
        // The progress dialog is still open and said "delivered successfully" at 100 %.
        bolusProgressData.updateProgress((given / requested * 100).toInt(), TextRef.Literal(text), PumpInsulin(given))
        rxBus.send(EventEquilAlarm(text))
    }

    fun stopBolus(bolusProfile: BolusProfile): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command: BaseCmd = CmdLargeBasalSet(0.0, aapsLogger, preferences, this)
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                command.waitMillis(command.timeOut.toLong())
            }
            bolusProfile.stop = command.cmdSuccess
            aapsLogger.debug(LTag.PUMPCOMM, "stopBolus===")
            result.success = command.cmdSuccess
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            loadEquilHistory()
            result.enacted(true)
        } catch (ex: Exception) {
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun loadEquilHistory(index: Int): Int {
        try {
            aapsLogger.debug(LTag.PUMPCOMM, "loadHistory start: ")
            val historyGet = CmdHistoryGet(index, aapsLogger, preferences, dateUtil, this)
            // Nothing was sent, so no answer will come: do not wait the full timeout for it.
            if (!equilBLE.readHistory(historyGet)) return -1
            synchronized(historyGet) {
                historyGet.waitMillis(historyGet.timeOut.toLong())
            }
            aapsLogger.debug(LTag.PUMPCOMM, "loadHistory end: ")
            if (!historyGet.cmdSuccess) return -1
            return historyGet.currentIndex
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return -1
    }

    fun addHistory(command: BaseCmd): EquilHistoryRecord {
        val equilHistoryRecord = EquilHistoryRecord(System.currentTimeMillis(), getSerialNumber())
        if (command.getEventType() != null) {
            equilHistoryRecord.type = command.getEventType()
        }
        if (command is CmdBasalSet) {
            val profile = command.profile
            equilHistoryRecord.basalValuesRecord = EquilBasalValuesRecord(listOf(*profile.getBasalValues()))
        }
        if (command is CmdTempBasalSet) {
            val cancel = command.cancel
            if (!cancel) {
                val equilTempBasalRecord =
                    EquilTempBasalRecord(
                        command.duration * 60 * 1000,
                        command.insulin, System.currentTimeMillis()
                    )
                equilHistoryRecord.tempBasalRecord = equilTempBasalRecord
            }
        }
        if (command is CmdExtendedBolusSet) {
            val cancel = command.cancel
            if (!cancel) {
                val equilTempBasalRecord = EquilTempBasalRecord(command.durationInMinutes * 60 * 1000, command.insulin, System.currentTimeMillis())
                equilHistoryRecord.tempBasalRecord = equilTempBasalRecord
            }
        }
        if (command is CmdLargeBasalSet) {
            val insulin = command.insulin
            if (insulin != 0.0) {
                val equilBolusRecord = EquilBolusRecord(insulin, BolusType.SMB, System.currentTimeMillis())
                equilHistoryRecord.bolusRecord = equilBolusRecord
            }
        }

        if (equilHistoryRecord.type != null) {
            val id = equilHistoryRecordDao.insert(equilHistoryRecord)
            equilHistoryRecord.id = id
            aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord is $id")
        }
        return equilHistoryRecord
    }

    fun updateHistory(equilHistoryRecord: EquilHistoryRecord, result: ResolvedResult) {
        aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord2 is ${equilHistoryRecord.id} $result")
        equilHistoryRecord.resolvedAt = System.currentTimeMillis()
        equilHistoryRecord.resolvedStatus = result
        val status = equilHistoryRecordDao.update(equilHistoryRecord)
        aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord3== is ${equilHistoryRecord.id} ${equilHistoryRecord.resolvedStatus} status $status")
    }

    fun readModeAndHistory(): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command: BaseCmd = CmdRunningModeGet(aapsLogger, preferences, this)
            equilBLE.writeCmd(command)
            synchronized(command) {
                command.waitMillis(command.timeOut.toLong())
            }
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
                SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                return loadEquilHistory()
            }
            result.success = command.cmdSuccess
            result.enacted(command.enacted)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun loadEquilHistory(): PumpEnactResult {
        SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
        val pumpEnactResult = pumpEnactResultProvider()
        var startIndex = getStartHistoryIndex() ?: return pumpEnactResult
        val index = getHistoryIndex() ?: return pumpEnactResult
        aapsLogger.debug(LTag.PUMPCOMM, "return ===$index====$startIndex")
        if (index == -1) {
            return pumpEnactResult.success(false)
        }
        var allCount = 1
        while (startIndex != index && allCount < 20) {
            startIndex++
            if (startIndex > 2000) {
                startIndex = 1
            }
            SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
            val currentIndex = loadEquilHistory(startIndex)
            aapsLogger.debug(LTag.PUMPCOMM, "while index===$startIndex===$index===$currentIndex")
            if (currentIndex > 1) {
                setStartHistoryIndex(currentIndex)
                allCount++
            } else {
                break
            }
        }
        return pumpEnactResult.success(true)
    }

    fun executeCmd(command: BaseCmd): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                command.waitMillis(command.timeOut.toLong())
            }
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            aapsLogger.debug(LTag.PUMPCOMM, "executeCmd result ${command.resolvedResult}")
            result.success = command.cmdSuccess
            result.enacted(command.enacted)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun readPodState(): String {
        return preferences.get(EquilStringKey.State)
    }

    /**
     * Reads the persisted pod state into memory.
     *
     * The new state is built in a local and published in one assignment. It used to null [equilState]
     * first and fill it in afterwards, which left a window where every concurrent reader saw no pod:
     * `hasPodState()` false, `getActivationProgress()` NONE. This object is a singleton read by the
     * overview screen, the wizard and the command queue while the BLE threads write to it, so that
     * window was reachable - it is what made `activatedPod_...` fail as "expected to be true" (#5040).
     */
    fun loadPodState() {
        val storedPodState = readPodState()

        val loaded = if (StringUtils.isEmpty(storedPodState)) {
            aapsLogger.info(LTag.PUMP, "loadPodState: no Pod state was provided")
            EquilState()
        } else {
            aapsLogger.info(LTag.PUMP, "loadPodState: serialized Pod state was provided: $storedPodState")
            try {
                gsonInstance.fromJson(storedPodState, EquilState::class.java) ?: EquilState()
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "loadPodState: could not deserialize PodState: $storedPodState", ex)
                EquilState()
            }
        }
        // The connection state is stored with the rest, but no BLE link survives an app restart. A stored
        // CONNECTING made the queue wait for a connect that was never started, until its 2 min timeout.
        loaded.bluetoothConnectionState = BluetoothConnectionState.DISCONNECTED

        equilState = loaded
        syncOverviewFlows()
    }

    private fun syncOverviewFlows() {
        val state = equilState ?: return
        lastConnectionFlow.value = state.lastDataTime
        reservoirFlow.value = state.currentInsulin.toDouble()
        batteryFlow.value = state.battery
        lastBolusTimeFlow.value = state.bolusRecord?.startTime
        lastBolusAmountFlow.value = state.bolusRecord?.amount
    }

    fun hasPodState(): Boolean = equilState != null // 0x0=discarded

    fun storePodState() {
        val podState = gsonInstance.toJson(this.equilState)
        aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: $podState")
        storePodState(podState)
    }

    fun clearPodState() {
        this.equilState = EquilState()
        val podState = gsonInstance.toJson(equilState)
        aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: $podState")
        storePodState(podState)
        syncOverviewFlows()
    }

    private fun storePodState(podState: String) {
        preferences.put(EquilStringKey.State, podState)
    }

    fun setAddress(address: String) {
        equilState?.address = address
        storePodState()
    }

    fun setBluetoothConnectionState(bluetoothConnectionState: BluetoothConnectionState) {
        equilState?.bluetoothConnectionState = bluetoothConnectionState
        storePodState()
    }

    fun setSerialNumber(serialNumber: String) {
        equilState?.serialNumber = serialNumber
        storePodState()
    }

    fun getSerialNumber(): String = equilState?.serialNumber ?: "UNKNOWN"

    fun getResistanceThreshold(): Int {
        val firstChar = getSerialNumber().substringAfterLast(" - ").firstOrNull()?.uppercaseChar()
        return if (firstChar in OLD_PUMP_SERIAL_PREFIXES) 500 else 220
    }

    fun setBolusRecord(bolusRecord: EquilBolusRecord?) {
        equilState?.bolusRecord = bolusRecord
        storePodState()
        lastBolusTimeFlow.value = bolusRecord?.startTime
        lastBolusAmountFlow.value = bolusRecord?.amount
    }

    fun getTempBasal(): EquilTempBasalRecord? = equilState?.tempBasal

    fun hasTempBasal(): Boolean = getTempBasal() != null

    fun isTempBasalRunning(): Boolean = isTempBasalRunningAt(null)

    fun isTempBasalRunningAt(at: DateTime?): Boolean {
        var time = at
        if (time == null) { // now
            if (!hasTempBasal()) {
                return true
            }
            time = DateTime.now()
        }
        getTempBasal()?.let { equilTempBasalRecord ->
            val tempBasalStartTime = DateTime(equilTempBasalRecord.startTime)
            val tempBasalEndTime = tempBasalStartTime.plus(equilTempBasalRecord.duration.toLong())
            return (time!!.isAfter(tempBasalStartTime) || time.isEqual(tempBasalStartTime)) && time.isBefore(tempBasalEndTime)
        }
        return false
    }

    fun setTempBasal(tempBasal: EquilTempBasalRecord?) {
        equilState?.tempBasal = tempBasal
        storePodState()
    }

    fun setBasalSchedule(basalSchedule: BasalSchedule) {
        equilState?.basalSchedule = basalSchedule
        storePodState()
    }

    fun setLastDataTime(lastDataTime: Long) {
        equilState?.lastDataTime = lastDataTime
        storePodState()
        lastConnectionFlow.value = lastDataTime
    }

    fun setCurrentInsulin(currentInsulin: Int) {
        equilState?.currentInsulin = currentInsulin
        storePodState()
        reservoirFlow.value = currentInsulin.toDouble()
    }

    fun setStartInsulin(startInsulin: Int) {
        aapsLogger.debug(LTag.PUMPCOMM, "startInsulin $startInsulin")
        equilState?.startInsulin = startInsulin
        storePodState()
    }

    /** null = not known, e.g. after a reservoir change, where the battery may have been swapped (#4477). */
    fun setBattery(battery: Int?) {
        equilState?.battery = battery
        storePodState()
        batteryFlow.value = battery
    }

    /**
     * Stores the new run mode. When the pump stops delivering (SUSPEND or STOP), a 0 U/h
     * PUMP_SUSPEND temp basal is recorded, so IOB does not count basal that was never given (#3830).
     * When the pump runs again, that record is ended and [EventEquilResumed] is sent. The first mode
     * after a fresh state is not a change, so nothing is recorded for it.
     */
    fun setRunMode(runMode: RunMode) {
        val previous = equilState?.runMode
        equilState?.runMode = runMode
        storePodState()
        if (previous == null || previous.isStopped() == runMode.isStopped()) return
        val time = dateUtil.now()
        val serial = getSerialNumber()
        launchPumpSync {
            // A "delivery stopped" gap that is still open ends here at the latest: the pump delivers again.
            val deliveryStoppedAt = if (runMode.isStopped()) null else equilState?.deliveryStoppedAt
            if (deliveryStoppedAt != null) {
                equilState?.deliveryStoppedAt = null
                storePodState()
                syncDeliveryStoppedRecord(deliveryStoppedAt, time - deliveryStoppedAt, serial)
            }
            syncRunMode(runMode.isStopped(), time, serial)
        }
        if (!runMode.isStopped()) rxBus.send(EventEquilResumed())
    }

    /**
     * Writes to pumpSync in the order the pump events happened. UNDISPATCHED takes the lock in the
     * calling thread, so a quick suspend and resume are not written the other way round.
     */
    private fun launchPumpSync(block: suspend () -> Unit) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runModeSyncMutex.withLock { block() }
        }
    }

    private fun RunMode.isStopped() = this == RunMode.SUSPEND || this == RunMode.STOP

    private suspend fun syncRunMode(stopped: Boolean, time: Long, serial: String) {
        val suspendRunning = pumpSync.expectedPumpState().temporaryBasal?.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND
        if (stopped && !suspendRunning) {
            aapsLogger.debug(LTag.PUMP, "Pump stopped delivery, recording 0 U/h PUMP_SUSPEND temp basal")
            pumpSync.syncTemporaryBasalWithPumpId(
                time,
                PumpRate(0.0),
                T.mins(4800).msecs(),
                true,
                PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                time,
                PumpType.EQUIL,
                serial
            )
        } else if (!stopped && suspendRunning) {
            aapsLogger.debug(LTag.PUMP, "Pump runs again, ending PUMP_SUSPEND temp basal")
            pumpSync.syncStopTemporaryBasalWithPumpId(time, time, PumpType.EQUIL, serial)
        }
    }

    fun setFirmwareVersion(firmwareVersion: String) {
        equilState?.firmwareVersion = firmwareVersion
        storePodState()
    }

    fun setRate(rate: Float) {
        equilState?.rate = rate
        storePodState()
    }

    fun getHistoryIndex(): Int? = equilState?.historyIndex

    fun setHistoryIndex(historyIndex: Int) {
        equilState?.historyIndex = historyIndex
        storePodState()
    }

    fun setActivationProgress(activationProgress: ActivationProgress) {
        equilState?.activationProgress = activationProgress
        storePodState()
    }

    fun getActivationProgress(): ActivationProgress {
        if (hasPodState()) {
            return Optional.ofNullable(equilState?.activationProgress).orElse(ActivationProgress.NONE)
        }
        return ActivationProgress.NONE
    }

    fun isActivationCompleted(): Boolean {
        return getActivationProgress() == ActivationProgress.COMPLETED
    }

    fun getStartHistoryIndex(): Int? = equilState?.startHistoryIndex

    fun setStartHistoryIndex(startHistoryIndex: Int) {
        equilState?.startHistoryIndex = startHistoryIndex
        storePodState()
    }

    class EquilState {

        var activationProgress: ActivationProgress? = null
        var serialNumber: String? = null
        var address: String? = null
        var firmwareVersion: String? = null

        var lastDataTime: Long = 0
        var currentInsulin = 0
        var startInsulin = 0
        var battery: Int? = null
        var tempBasal: EquilTempBasalRecord? = null
        var bolusRecord: EquilBolusRecord? = null
        var runMode: RunMode? = null

        /** Pump time of a "battery/reservoir empty" record whose 0 U/h gap is not ended yet, see syncDeliveryStoppedGap. */
        var deliveryStoppedAt: Long? = null
        var rate = 0f
        var historyIndex = 0

        var bluetoothConnectionState: BluetoothConnectionState? = BluetoothConnectionState.DISCONNECTED
        var startHistoryIndex = 0
        var basalSchedule: BasalSchedule? = null
    }

    fun setRunMode(mode: Int) {
        when (mode) {
            0    -> setRunMode(RunMode.SUSPEND)
            1    -> setRunMode(RunMode.RUN)
            2    -> setRunMode(RunMode.STOP)
            else -> setRunMode(RunMode.SUSPEND)
        }
        rxBus.send(EventEquilModeChanged())
    }

    fun setInsulinChange(status: Int) {
        if (status == 1) {
            rxBus.send(EventEquilInsulinChanged())
        }
    }

    fun decodeHistory(data: ByteArray) {
        var year = data[6].toInt() and 0xff
        year += 2000

        val month = data[7].toInt() and 0xff
        val day = data[8].toInt() and 0xff
        val hour = data[9].toInt() and 0xff
        val min = data[10].toInt() and 0xff
        val second = data[11].toInt() and 0xff
        //a5e207590501 17070e100f161100000000007d0204080000
        //ae6ae9100501 17070e100f16 1100000000007d0204080000
        val battery = data[12].toInt() and 0xff
        val insulin = data[13].toInt() and 0xff
        val rate = bytesToInt(data[15], data[14])
        val largeRate = bytesToInt(data[17], data[16])
        val index = bytesToInt(data[19], data[18])

        val port = data[20].toInt() and 0xff
        val type = data[21].toInt() and 0xff
        val level = data[22].toInt() and 0xff
        val parm = data[23].toInt() and 0xff
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, min)
        calendar.set(Calendar.SECOND, second)
        calendar.set(Calendar.MILLISECOND, 0)
        val equilHistoryPump = EquilHistoryPump()
        equilHistoryPump.battery = battery
        equilHistoryPump.insulin = insulin
        equilHistoryPump.rate = rate
        equilHistoryPump.largeRate = largeRate
        equilHistoryPump.timestamp = System.currentTimeMillis()
        // The pump time has whole seconds only, and several events often share one second. Adding the
        // event index as milliseconds keeps their timestamps different. The index is the absolute history
        // index, so the stored time is late by index ms (about 2 s at index 1700, at most 65 s).
        equilHistoryPump.eventTimestamp = (calendar.getTimeInMillis() + index)
        equilHistoryPump.port = port
        equilHistoryPump.type = type
        equilHistoryPump.level = level
        equilHistoryPump.parm = parm
        equilHistoryPump.eventIndex = index
        equilHistoryPump.serialNumber = getSerialNumber()
        val id = equilHistoryPumpDao.insert(equilHistoryPump)
        aapsLogger.debug(LTag.PUMPCOMM, "decodeHistory insert id $id")
        val eventTime = calendar.timeInMillis
        val errorTips = getEquilError(port, type, level)
        if (errorTips.isNotEmpty()) raiseAlarm(errorTips, eventTime)
        syncDeliveryStoppedGap(port, type, level, rate, eventTime)
        recordInCareportal(port, type, level, errorTips, eventTime)
        rxBus.send(EventEquilDataChanged())
    }

    /**
     * Pump alarms come from the pump history: every record is read once, in order, after a connection.
     * The advertisement is caught only some of the time, and it showed the same alarm again on every
     * connection (#5077). An alarm that is only read late does not sound as if it had just happened.
     */
    private fun raiseAlarm(text: String, eventTime: Long) {
        val record = EquilHistoryRecord(EquilHistoryRecord.EventType.EQUIL_ALARM, eventTime, getSerialNumber())
        record.resolvedAt = System.currentTimeMillis()
        record.resolvedStatus = ResolvedResult.SUCCESS
        record.note = text
        equilHistoryRecordDao.insert(record)
        val recent = dateUtil.now() - eventTime < T.mins(ALARM_SOUND_MAX_AGE_MINUTES).msecs()
        rxBus.send(EventEquilAlarm(text, sound = recent))
    }

    /**
     * Pump alarms and events that stop delivery are also written to the careportal as an announcement,
     * at the pump's own time, so the user can see later when and why the pump stopped (#4132). Battery
     * empty is not an alarm (the pump is dead then), but it is recorded. Reading the same record again
     * does not create a second entry: the event is only inserted if none exists at that time.
     */
    private fun recordInCareportal(port: Int, type: Int, level: Int, errorTips: String, eventTime: Long) {
        val note = errorTips.ifEmpty {
            if (PumpEvent.stopsDelivery(port, type, level)) PumpEvent.getEventStringRes(port, type, level)?.let { rh.gs(it) } ?: "" else ""
        }
        if (note.isEmpty()) return
        val serial = getSerialNumber()
        launchPumpSync {
            pumpSync.insertTherapyEventIfNewWithTimestamp(eventTime, TE.Type.ANNOUNCEMENT, note, null, PumpType.EQUIL, serial)
        }
    }

    /**
     * Pump time of the last "battery empty" or "reservoir empty" record read, for the bolus check in
     * [bolus]. Not stored: it only matters right after the bolus, in the same process.
     */
    @Volatile private var lastDeliveryStoppedAt: Long? = null

    /**
     * When the battery dies or the reservoir runs empty, the pump logs it and stops all delivery in the
     * same second, basal too. AAPS kept counting basal that was never given (#3830, #3467). Record a
     * 0 U/h PUMP_SUSPEND temp basal from that moment until the first later record that shows delivery
     * again (rate > 0), or until the next RUN. The start is the pump's own time, so the gap is never
     * made longer than it was.
     */
    private fun syncDeliveryStoppedGap(port: Int, type: Int, level: Int, rate: Int, eventTime: Long) {
        val serial = getSerialNumber()
        val stops = PumpEvent.stopsDelivery(port, type, level)
        if (stops) lastDeliveryStoppedAt = eventTime
        // In the pumpSync lock, so records are handled one after the other, in pump order.
        launchPumpSync {
            val state = equilState ?: return@launchPumpSync
            if (stops) {
                // The run mode read before this history (STOP after an empty reservoir) may already have
                // recorded a suspend a few seconds later. Then fill only the time before it, no overlap.
                val suspend = pumpSync.expectedPumpState().temporaryBasal
                    ?.takeIf { it.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND && it.timestamp > eventTime }
                if (suspend != null) {
                    syncDeliveryStoppedRecord(eventTime, suspend.timestamp - eventTime, serial)
                } else {
                    state.deliveryStoppedAt = eventTime
                    storePodState()
                    syncDeliveryStoppedRecord(eventTime, T.mins(4800).msecs(), serial)
                }
                return@launchPumpSync
            }
            val start = state.deliveryStoppedAt ?: return@launchPumpSync
            if (rate > 0 && eventTime > start) {
                state.deliveryStoppedAt = null
                storePodState()
                syncDeliveryStoppedRecord(start, eventTime - start, serial)
            }
        }
    }

    /** Same pump id for the open record and for its end, so the second call only sets the duration. */
    private suspend fun syncDeliveryStoppedRecord(start: Long, duration: Long, serial: String) {
        aapsLogger.debug(LTag.PUMP, "Delivery stopped: 0 U/h PUMP_SUSPEND temp basal from ${dateUtil.dateAndTimeString(start)} for ${duration / 60000} min")
        pumpSync.syncTemporaryBasalWithPumpId(
            start,
            PumpRate(0.0),
            duration,
            true,
            PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            start,
            PumpType.EQUIL,
            serial
        )
    }

    fun decodeData(data: ByteArray) {
        // data[11..16] = pump date and time (year - 2000, month, day, hour, minute, second), not used here
        val battery = data[17].toInt() and 0xff
        val insulin = data[18].toInt() and 0xff
        val rate1 = bytesToInt(data[20], data[19])
        val rate = internalDecodeSpeedToUH(rate1)
        //val largeRate = bytesToInt(data[22], data[21]).toFloat()
        val historyIndex = bytesToInt(data[24], data[23])
        // data[25..27] = port, type, level of the newest history event. Alarms are not raised from here
        // but from the history record itself (see raiseAlarm), so each one only once.
        val port = data[25].toInt() and 0xff
        val type = data[26].toInt() and 0xff
        val level = data[27].toInt() and 0xff
        aapsLogger.debug(LTag.PUMPCOMM, "decodeData historyIndex $historyIndex newest event port:$port type:$type level:$level")
        setHistoryIndex(historyIndex)
        setLastDataTime(System.currentTimeMillis())
        setCurrentInsulin(insulin)
        setBattery(battery)
        setRate(rate)
        rxBus.send(EventEquilDataChanged())
    }

    companion object {

        /** A pump alarm read later than this after it happened is shown without sound. */
        const val ALARM_SOUND_MAX_AGE_MINUTES = 30L

        /** Bolus step 0.05 U. */
        private const val BOLUS_STEPS_PER_UNIT = 20.0

        val OLD_PUMP_SERIAL_PREFIXES = setOf('0', '1', '3', 'A', 'D')

        private fun createGson(): Gson {
            val gsonBuilder = GsonBuilder()
                .registerTypeAdapter(DateTime::class.java, JsonSerializer { dateTime: DateTime?, _, _ -> JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)) })
                .registerTypeAdapter(DateTime::class.java, JsonDeserializer { json: JsonElement?, _, _ -> ISODateTimeFormat.dateTime().parseDateTime(json!!.asString) })
                .registerTypeAdapter(DateTimeZone::class.java, JsonSerializer { timeZone: DateTimeZone?, _, _ -> JsonPrimitive(timeZone!!.id) })
                .registerTypeAdapter(DateTimeZone::class.java, JsonDeserializer { json: JsonElement?, _, _ -> DateTimeZone.forID(json!!.asString) })

            return gsonBuilder.create()
        }
    }
}
