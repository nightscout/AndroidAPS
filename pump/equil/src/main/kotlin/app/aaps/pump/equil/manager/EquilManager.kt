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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import java.util.Calendar
import java.util.Optional
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    private val podStateLock = Any()

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
        synchronized(podStateLock) {
            if (!podStateLoaded) {
                loadPodState()
                podStateLoaded = true
            }
        }
        equilBLE.init(this)
    }

    fun getEquilError(port: Int, type: Int, level: Int): String =
        PumpEvent.getErrorString(rh, port, type, level)

    fun closeBleAuto() {
        equilBLE.closeBleAuto()
    }

    /** The driver stops: the link closes and no new one opens until [startLink], see `EquilBLE.stop`. */
    fun stopLink() = equilBLE.stop()

    fun startLink() = equilBLE.start()

    fun connect() {
        equilBLE.connect("EquilManager::connect")
    }

    fun getTempBasalPump(): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command = CmdTempBasalGet(aapsLogger, preferences, this)
            equilBLE.writeCmd(command)
            awaitAnswer(command)
            result.success = command.cmdSuccess
            result.enacted(command.time != 0)
            SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    /**
     * Waits for the answer of [command], at most its timeout. The answer can come before this wait begins: its wake-up
     * then reached nobody, and a plain wait slept the whole timeout for a command that was done. Found by the pump
     * scenario tests, where pairing hung at the password step. So the wait is for the answer itself, and a wake-up
     * without one does not end it.
     */
    internal fun awaitAnswer(command: BaseCmd, timeoutMs: Long = command.timeOut.toLong()) {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(command) {
            while (!command.cmdSuccess && !command.failed) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                command.waitMillis(left)
            }
        }
    }

    /** Whether the pump runs a temp basal now, or null when it does not answer. */
    private fun tempBasalRunsOnPump(): Boolean? = getTempBasalPump().takeIf { it.success }?.enacted

    /**
     * Ends the temp basal that AAPS keeps when the pump runs none. A cancel whose answer is lost, and then a lost read
     * of what the pump runs, leave AAPS with a temp basal the pump has ended, and nothing else corrects it. The pump is
     * read only while AAPS has a temp basal. A suspend is a PUMP_SUSPEND record that the run mode ends, so it is left
     * alone. Found by the pump scenario tests.
     */
    suspend fun endTempBasalThePumpDoesNotRun() {
        val tempBasal = pumpSync.expectedPumpState().temporaryBasal ?: return
        if (tempBasal.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND) return
        endTempBasalWhenThePumpRunsNone(tempBasalRunsOnPump(), tempBasal.timestamp)
    }

    /**
     * @param runsOnPump what the pump said, null when it did not answer: then nothing is decided
     * @param since when the temp basal of AAPS started, for the log
     */
    internal suspend fun endTempBasalWhenThePumpRunsNone(runsOnPump: Boolean?, since: Long) {
        if (runsOnPump != false) return
        // The pump gives no end time, so the temp basal ends at this read.
        val now = dateUtil.now()
        if (!pumpSync.syncStopTemporaryBasalWithPumpId(now, now, PumpType.EQUIL, getSerialNumber())) return
        setTempBasal(null)
        aapsLogger.warn(LTag.PUMPCOMM, "Status: the pump runs no temp basal, but AAPS had one since ${dateUtil.dateAndTimeString(since)}. Ended it now.")
    }

    suspend fun setTempBasal(insulin: Double, time: Int, cancel: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command = CmdTempBasalSet(insulin, time, aapsLogger, preferences, this)
            command.cancel = cancel
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            awaitAnswer(command)
            var applied = command.cmdSuccess
            // Without its answer AAPS knew nothing, while the pump may have done it: what the pump runs now decides.
            var currentTime = System.currentTimeMillis()
            if (!applied && command.commandSent) {
                SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                val runs = tempBasalRunsOnPump()
                aapsLogger.warn(LTag.PUMPCOMM, "Temp basal ${if (cancel) "cancel" else "start"} got no answer. The pump runs a temp basal: $runs")
                if (runs != null && runs != cancel) {
                    applied = true
                    // It took effect when it reached the pump, not now.
                    currentTime = command.createTime
                }
            }
            if (applied) {
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
            result.success = applied
            result.enacted(applied)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    suspend fun setExtendedBolus(insulin: Double, time: Int, cancel: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val indexBefore = getHistoryIndex()
            val command = CmdExtendedBolusSet(insulin, time, cancel, aapsLogger, preferences, this)
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            awaitAnswer(command)

            result.success = command.cmdSuccess
            // Without an answer the pump may have done it. AAPS keeps what counts more insulin, and the log decides later.
            val unanswered = !command.cmdSuccess && command.commandSent
            if (unanswered) {
                aapsLogger.warn(LTag.PUMPCOMM, "Extended bolus ${if (cancel) "cancel" else "start"} got no answer: decided from the pump log")
                if (indexBefore != null)
                    uncertainExtended = UncertainExtended(start = !cancel, largeRate = extendedLargeRate(command), indexBefore = indexBefore, sentAt = command.createTime)
                if (!cancel) {
                    pumpSync.syncExtendedBolusWithPumpId(
                        command.createTime, PumpRate(insulin), time.toLong() * 60 * 1000, false, command.createTime, PumpType.EQUIL, getSerialNumber()
                    )
                    result.enacted(true)
                }
            }
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
                        // A real extended bolus: Equil has its own temp basals. With true, NS sync could upload it as a
                        // temp basal and autotune counted it as basal.
                        false,
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

    /** See [UncertainExtended]. Set by [setExtendedBolus], decided by [decideUncertainExtended]. */
    @Volatile internal var uncertainExtended: UncertainExtended? = null

    /**
     * The speed of an extended bolus [command] as the pump log shows it, in steps of 0.00625 U/h: the command has 1/160 U
     * steps and seconds. A real pump logged 160 for 0.5 U in 30 min.
     */
    private fun extendedLargeRate(command: CmdExtendedBolusSet): Int =
        if (command.pumpTime <= 0) 0
        else (command.step / COMMAND_STEPS_PER_UNIT / command.pumpTime * SECONDS_PER_HOUR / LOG_SPEED_UNIT).roundToInt() and 0xFFFF

    /**
     * Decides the [uncertainExtended] from the log read just now. A start in the log keeps the record, a cancel in the log
     * ends the extended bolus at its time. Nothing in the log after a read that reached the advertised index at a
     * connection made after [UncertainExtended.notDoneAfter]: the command did not reach the pump, so a start is taken
     * back and a cancel leaves the extended bolus running.
     * @param complete the read reached the advertised index
     */
    @VisibleForTesting
    internal fun decideUncertainExtended(complete: Boolean) {
        val extended = uncertainExtended ?: return
        val doneAt = extended.doneAt
        if (doneAt == null && (!complete || advertisedAt < extended.notDoneAfter)) return
        uncertainExtended = null
        val serial = getSerialNumber()
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus ${if (extended.start) "start" else "cancel"} from the pump log: ${if (doneAt != null) "done" else "not done"}")
        // Stored before the read ends, so the status read that decided it shows it (a scenario checked 0.3 s later and
        // found the extended bolus still running).
        runBlocking {
            runModeSyncMutex.withLock {
                when {
                    extended.start && doneAt == null  -> pumpSync.syncStopExtendedBolusWithPumpId(extended.sentAt, extended.sentAt + 1, PumpType.EQUIL, serial)
                    !extended.start && doneAt != null -> {
                        // The log has whole seconds of the pump clock, which can be behind the phone (5 s on a real pump):
                        // a cancel soon after the start can be logged before the start AAPS recorded, and nothing ran then.
                        // The end must be after the start, or the database refuses it.
                        val since = pumpSync.expectedPumpState().extendedBolus?.timestamp ?: doneAt
                        val end = max(doneAt, since + 1)
                        pumpSync.syncStopExtendedBolusWithPumpId(end, end, PumpType.EQUIL, serial)
                    }
                    else                              -> Unit
                }
            }
        }
    }

    /** What came of a bolus command, as far as the driver can know it. */
    internal enum class BolusStart {

        /** The pump answered: it gives the bolus. */
        STARTED,

        /** The command never went to the pump, so it gives nothing. */
        NOT_SENT,

        /** The command went out, and its answer did not come: the pump may give the bolus. */
        NO_ANSWER
    }

    internal fun bolusStart(command: BaseCmd): BolusStart = when {
        command.cmdSuccess  -> BolusStart.STARTED
        command.commandSent -> BolusStart.NO_ANSWER
        else                -> BolusStart.NOT_SENT
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
            // Before the command: a stop that comes while it waits for its answer must not be undone.
            bolusProfile.stoppedAt = 0
            bolusProfile.stop = false
            bolusProfile.stopRequested = false
            // The log entries of this bolus come after this index, see UncertainBolus.
            val indexBefore = getHistoryIndex()
            equilBLE.writeCmd(command)
            awaitAnswer(command)
            // The pump starts when the command reaches it, before its answer. Counted from the sending, so a stop
            // never makes AAPS count less than was given.
            val bolusStart = command.commandSentAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            val unitsPerSecond = detailedBolusInfo.insulin / command.stepTime
            var stoppedAmount: Double? = null
            val sleep = 2000
            // The pump gives the bolus in command.stepTime seconds; the progress follows that time.
            val percent1 = 100f * (sleep / 1000f) / command.stepTime
            aapsLogger.debug(LTag.PUMPCOMM, "sleep===" + detailedBolusInfo.insulin + "===" + percent1)
            var percent = 0f
            val start = bolusStart(command)
            // The pump gives the whole bolus also when the app is killed meanwhile, so it is recorded now with the whole
            // amount and corrected at the end. Recorded only at the end, a killed app left no record at all (found by the
            // app restart scenario). The bolus time is its start.
            if (start != BolusStart.NOT_SENT) recordBolus(bolusStart, detailedBolusInfo.insulin, detailedBolusInfo)
            when (start) {
                BolusStart.STARTED   -> {
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
                    // A stop the pump answered: what it gave until then comes from the times, not from the timer, which
                    // steps every 2 s. A stop without an answer is no stop: the whole amount counts.
                    val stoppedAt = bolusProfile.stoppedAt
                    if (bolusProfile.stop && stoppedAt > 0)
                        stoppedAmount = unitsGivenUntil(bolusStart, stoppedAt, unitsPerSecond, detailedBolusInfo.insulin)
                    if (bolusProfile.stopRequested && !bolusProfile.stop) {
                        // The user pressed stop and the pump did not answer it: "delivered successfully" would hide that
                        // the stop may not have worked. The pump log decides later, see UncertainBolus.
                        val requested = PumpInsulin(detailedBolusInfo.insulin)
                        val text = rh.gs(R.string.equil_stop_not_answered, ch.insulinAmountString(requested))
                        bolusProgressData.updateProgress(100, TextRef.Literal(text), requested)
                        result.comment = text
                    } else {
                        bolusProgressData.updateProgress(percent = 100)
                        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
                    }
                }

                // The pump may give it: the whole amount counts, and the user must not bolus again.
                BolusStart.NO_ANSWER -> {
                    aapsLogger.warn(LTag.PUMPCOMM, "The bolus command went out and got no answer: the pump may give it")
                    result.success = false
                    result.enacted(true)
                    percent = 100f
                    result.comment = rh.gs(R.string.equil_bolus_outcome_unknown, ch.insulinAmountString(PumpInsulin(detailedBolusInfo.insulin)))
                }

                BolusStart.NOT_SENT  -> {
                    result.success = false
                    result.enacted(false)
                    result.comment = rh.gs(R.string.equil_command_connect_error)
                }
            }
            result.bolusDelivered = stoppedAmount ?: (percent / 100.0 * detailedBolusInfo.insulin)
            if (result.enacted) {
                if (start == BolusStart.STARTED) command.resolvedResult = ResolvedResult.SUCCESS
                recordBolus(bolusStart, result.bolusDelivered, detailedBolusInfo)
                // The start got no answer, or a stop had none yet when the bolus ended: the pump log tells what was
                // given, once the bolus is over. A stop that never reached the pump leaves the whole bolus in the log.
                val stopNotAnswered = bolusProfile.stopRequested && !bolusProfile.stop
                if (indexBefore != null && (start == BolusStart.NO_ANSWER || stopNotAnswered)) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Bolus outcome not known: decided from the pump log after the bolus")
                    uncertainBolus = UncertainBolus(
                        requested = detailedBolusInfo.insulin,
                        unitsPerSecond = unitsPerSecond,
                        indexBefore = indexBefore,
                        sentAt = bolusStart,
                        decideAfter = bolusStart + command.stepTime * 1000L + UNCERTAIN_BOLUS_MARGIN_MS,
                        bolusType = detailedBolusInfo.bolusType,
                        pumpId = detailedBolusInfo.timestamp,
                        recordTime = bolusStart
                    )
                }
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            // The pump closes the link about 10 s after the bolus command, so after a longer bolus there is
            // no link left. Then connect again and read the run mode and history now: otherwise a pump alarm
            // during the bolus (e.g. empty reservoir) was only seen at the next loop run (#3467).
            if (equilBLE.isConnected) loadEquilHistory() else readModeAndHistory()
            if (result.success) correctBolusIfDeliveryStopped(result, detailedBolusInfo, bolusStart, bolusStart, unitsPerSecond)
        } catch (ex: Exception) {
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    /** The AAPS record of the bolus of [detailedBolusInfo], at [time], with [units]. Called again, it corrects the same record. */
    private suspend fun recordBolus(time: Long, units: Double, detailedBolusInfo: DetailedBolusInfo) {
        pumpSync.syncBolusWithPumpId(time, PumpInsulin(units), detailedBolusInfo.bolusType, detailedBolusInfo.timestamp, PumpType.EQUIL, getSerialNumber())
        setBolusRecord(EquilBolusRecord(units, BolusType.SMB, time))
    }

    /**
     * What a bolus given at [unitsPerSecond] from [start] gave until [end]. Rounded up to the 0.05 U step and at most
     * [requested]: AAPS must never count less insulin than was given.
     */
    internal fun unitsGivenUntil(start: Long, end: Long, unitsPerSecond: Double, requested: Double): Double {
        val units = (end - start).coerceAtLeast(0) / 1000.0 * unitsPerSecond
        return min(requested, ceil(units * BOLUS_STEPS_PER_UNIT - 1e-9) / BOLUS_STEPS_PER_UNIT)
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
        val given = unitsGivenUntil(bolusStart, stoppedAt, unitsPerSecond, requested)
        if (given >= requested) return
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery stopped during the bolus: $given of $requested U")
        result.bolusDelivered = given
        pumpSync.syncBolusWithPumpId(bolusTime, PumpInsulin(given), detailedBolusInfo.bolusType, detailedBolusInfo.timestamp, PumpType.EQUIL, getSerialNumber())
        setBolusRecord(EquilBolusRecord(given, BolusType.SMB, bolusTime))
        val text = rh.gs(R.string.equil_bolus_stopped, ch.insulinAmountString(PumpInsulin(given)), ch.insulinAmountString(PumpInsulin(requested)))
        // The progress dialog is still open and said "delivered successfully" at 100 %.
        bolusProgressData.updateProgress((given / requested * 100).toInt(), TextRef.Literal(text), PumpInsulin(given))
        rxBus.send(EventEquilAlarm(text))
        // Not given in full: so the bolus alarm of AAPS rings too, as for any bolus the pump did not give.
        result.success = false
        result.comment = text
    }

    /** See [UncertainBolus]. Set by [bolus], decided by [decideUncertainBolus]. */
    @Volatile internal var uncertainBolus: UncertainBolus? = null

    /** Phone time of the last advertisement, which brings the newest log index. Answers set lastDataTime too. */
    @Volatile private var advertisedAt: Long = 0

    /**
     * Decides the [uncertainBolus] after a read that reached the newest index the pump advertised at a connection made
     * after the bolus was over. No bolus in the log after the command: not given. A start and an end: (end - start + 1 s)
     * x the speed of the command, as the pump time has whole seconds. It is rounded up and at most the amount asked for,
     * so AAPS never counts less than was given. A start without an end yet waits for the next read, and "not given"
     * waits for an advertisement from [UncertainBolus.notGivenAfter] on.
     * @param complete the read reached the advertised index
     */
    @VisibleForTesting
    internal fun decideUncertainBolus(complete: Boolean) {
        val bolus = uncertainBolus ?: return
        if (!complete || advertisedAt < bolus.decideAfter) return
        val logged = bolus.inLog()
        val end = logged?.end
        if (logged != null && end == null) return
        if (logged == null && advertisedAt < bolus.notGivenAfter) return
        uncertainBolus = null
        val given = if (logged == null || end == null) 0.0
        else unitsGivenUntil(logged.start, end + T.secs(1).msecs(), bolus.unitsPerSecond, bolus.requested)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus outcome from the pump log: $given of ${bolus.requested} U")
        if (given >= bolus.requested) return
        val serial = getSerialNumber()
        launchPumpSync {
            pumpSync.syncBolusWithPumpId(bolus.recordTime, PumpInsulin(given), bolus.bolusType, bolus.pumpId, PumpType.EQUIL, serial)
        }
        setBolusRecord(EquilBolusRecord(given, BolusType.SMB, bolus.recordTime))
        val requested = ch.insulinAmountString(PumpInsulin(bolus.requested))
        val text = if (given == 0.0) rh.gs(R.string.equil_bolus_not_given, requested)
        else rh.gs(R.string.equil_bolus_stopped, ch.insulinAmountString(PumpInsulin(given)), requested)
        // Information, not an alarm: the pump did less than AAPS counted, never more.
        rxBus.send(EventEquilAlarm(text, sound = false))
    }

    fun stopBolus(bolusProfile: BolusProfile): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val command: BaseCmd = CmdLargeBasalSet(0.0, aapsLogger, preferences, this)
            val equilHistoryRecord = addHistory(command)
            bolusProfile.stopRequested = true
            equilBLE.writeCmd(command)
            awaitAnswer(command)
            // The pump stopped when the stop reached it, at the latest now. Counted from now: never less than was given.
            if (command.cmdSuccess) bolusProfile.stoppedAt = System.currentTimeMillis()
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
            awaitAnswer(historyGet)
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
            awaitAnswer(command)
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
            // -1 is a failed read. Index 1 is an entry like any other: with "> 1" the read stopped there every time,
            // so after the log started again at 1 the driver read no more of it (found by the pump scenario tests).
            if (currentIndex > 0) {
                setStartHistoryIndex(currentIndex)
                allCount++
            } else {
                break
            }
        }
        decideUncertainBolus(complete = startIndex == index)
        decideUncertainExtended(complete = startIndex == index)
        return pumpEnactResult.success(true)
    }

    fun executeCmd(command: BaseCmd): PumpEnactResult {
        val result = pumpEnactResultProvider()
        try {
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            awaitAnswer(command)
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

    /**
     * Resets the pod state, in memory and in preferences.
     *
     * Under the same lock as the load in [init], and it counts as that load. Without this, `onStart` on
     * its background coroutine could read the old state before the reset and publish it after, or read
     * something stale after it. Memory then says COMPLETED while preferences hold the clean state.
     */
    fun clearPodState() {
        synchronized(podStateLock) {
            this.equilState = EquilState()
            // The log of another pump says nothing about it.
            uncertainBolus = null
            podStateLoaded = true
            val podState = gsonInstance.toJson(equilState)
            aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: $podState")
            storePodState(podState)
            syncOverviewFlows()
        }
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

    /** Stores the schedule the pump accepted, with the hourly rates it reported for it, or null when they were not read. */
    fun setBasalSchedule(basalSchedule: BasalSchedule, reportedBasal: List<Double>? = null) {
        equilState?.basalSchedule = basalSchedule
        equilState?.reportedBasal = reportedBasal
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
        // AAPS ends the temp basal at a stop (syncRunMode), and after a resume the driver cancels it on the pump: the
        // driver's own record ends here too, or the Equil screen goes on showing it.
        if (runMode.isStopped() && previous?.isStopped() == false) equilState?.tempBasal = null
        storePodState()
        val time = dateUtil.now()
        val serial = getSerialNumber()
        // A stop for a reservoir change or a removal ends all the pod gives, also an extended bolus. Left running, AAPS
        // counted it after the pod was removed, also when the pod could not be reached any more (D9).
        if (runMode == RunMode.STOP) launchPumpSync { endExtendedBolus(time, serial) }
        if (previous == null || previous.isStopped() == runMode.isStopped()) return
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

    private suspend fun endExtendedBolus(time: Long, serial: String) {
        if (pumpSync.expectedPumpState().extendedBolus == null) return
        aapsLogger.debug(LTag.PUMP, "Pump stopped, ending the extended bolus")
        pumpSync.syncStopExtendedBolusWithPumpId(time, time, PumpType.EQUIL, serial)
    }

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

        /** The hourly rates the pump reported for [basalSchedule] after it was set, see CmdBasalGet. null when not read. */
        var reportedBasal: List<Double>? = null
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
        // All 16 bits: bytesToInt drops the top one, and a speed of 6 U/min (57600) needs it.
        val largeRate = ((data[17].toInt() and 0xff) shl 8) or (data[16].toInt() and 0xff)
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
        uncertainBolus?.onLogEntry(index, port, type, level, largeRate, eventTime)
        uncertainExtended?.onLogEntry(index, port, type, largeRate, eventTime)
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
        // Index 0 is no log at all (the pump counts from 1), so it says nothing about the newest entry. Such an
        // advertisement once made a given bolus look "not given" (an emulator that handed out an old advertisement).
        if (historyIndex > 0) advertisedAt = System.currentTimeMillis()
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

        /** The steps of a bolus or extended bolus command, see `Utils.unitsToSteps`. */
        private const val COMMAND_STEPS_PER_UNIT = 160.0
        private const val SECONDS_PER_HOUR = 3600.0

        /** The speed unit of the pump log: 0.00625 U/h. */
        private const val LOG_SPEED_UNIT = 0.00625

        /** After the end a bolus would have, by the phone clock: its last log entry is written by then. */
        private const val UNCERTAIN_BOLUS_MARGIN_MS = 3_000L

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
