package app.aaps.pump.aaruy

import android.util.Base64
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasalItem
import app.aaps.pump.aaruy.comm.BasalItems
import app.aaps.pump.aaruy.comm.BasicParam
import com.google.gson.Gson
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.common.util.AaruyPumpSchedule
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AaruyType
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.keys.AaruyBooleanKey
import app.aaps.pump.aaruy.keys.AaruyDoubleKey
import app.aaps.pump.aaruy.keys.AaruyDoublePreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import app.aaps.pump.aaruy.keys.AaruyLongKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.EnumSet
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SingleIn(AppScope::class)
@Inject
class AaruyPump(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val bolusProgressData: BolusProgressData
)  {

    companion object {

        const val FAKE_TBR_LENGTH = 1440L
        private const val BASAL_STEP = 0.025
        private const val BOLUS_PRECISION_UNITS = 0.025
        private const val SECONDS_PER_HALF_HOUR = 1800
    }

    // Connection state flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionStateFlow: StateFlow<ConnectionState> = _connectionState
    var connectionState: ConnectionState
        get() = _connectionState.value
        set(value) {
            _connectionState.value = value
        }

    // Pump error code
    // Not a state: the pump sends the same message for each prime.
    val errCode = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    // Pump state flow
    private val _pumpState = MutableStateFlow(AaruyPumpState.FIRST_INIT)
    val pumpStateFlow: StateFlow<AaruyPumpState> = _pumpState
    var pumpState: AaruyPumpState
        get() = _pumpState.value
        set(value) {
            _pumpState.value = value
            preferences.put(AaruyIntKey.PumpState, value.state.toInt())
        }

    private val _pumpLastState = MutableStateFlow(AaruyPumpState.FIRST_INIT)
    val pumpLastStateFlow: StateFlow<AaruyPumpState> = _pumpLastState
    var pumpLastState: AaruyPumpState
        get() = _pumpLastState.value
        set(value) {
            _pumpLastState.value = value
            preferences.put(AaruyIntKey.PumpLastState, value.state.toInt())
        }

    // Active alarms
    private var _activeAlarms: EnumSet<AlarmState> = EnumSet.noneOf(AlarmState::class.java)
    var activeAlarms: EnumSet<AlarmState>
        get() = _activeAlarms
        set(value) {
            _activeAlarms = value
        }

    // New pump warnings
    private val _pumpWarning = MutableStateFlow(AlarmState.NONE)
    val pumpWarningFlow: StateFlow<AlarmState> = _pumpWarning
    var pumpWarning: AlarmState
        get() = _pumpWarning.value
        set(value) {
            _pumpWarning.value = value
        }

    private val _lastBasalRate = MutableStateFlow(0.0)
    val lastBasalRateFlow: StateFlow<Double> = _lastBasalRate
    val lastBasalRate: Double
        get() = _lastBasalRate.value

    private val _reservoir = MutableStateFlow(0.0)
    val reservoirFlow: StateFlow<Double> = _reservoir
    var reservoir: Double
        get() = _reservoir.value
        set(value) {
            _reservoir.value = value
        }

    private val _remainBattery = MutableStateFlow(0.0)
    val remainBatteryFlow: StateFlow<Double> = _remainBattery
    var remainBattery: Double
        get() = _remainBattery.value
        set(value) {
            _remainBattery.value = value
        }

    private var _actualBasalProfile = byteArrayOf(0)
    var actualBasalProfile: ByteArray
        get() = _actualBasalProfile
        set(value) {
            _actualBasalProfile = value
            val encodedString = Base64.encodeToString(value, Base64.DEFAULT)
            preferences.put(AaruyStringKey.PumpActualBasalProfile, encodedString ?: "")
        }

    private var _curBolusTime = 0L // Time in ms!
    var curBolusTime: Long
        get() = _curBolusTime
        set(value) {
            _curBolusTime = value
            preferences.put(AaruyLongKey.PumpCurBolusTime, value)
        }

    private val _lastBolusTime = MutableStateFlow(0L) // Time in ms!
    val lastBolusTimeFlow: StateFlow<Long> = _lastBolusTime
    var lastBolusTime: Long
        get() = _lastBolusTime.value
        set(value) {
            _lastBolusTime.value = value
            preferences.put(AaruyLongKey.PumpLastBolusTime, value)
        }

    private val _lastBolusAmount = MutableStateFlow(0.0)
    val lastBolusAmountFlow: StateFlow<Double> = _lastBolusAmount
    var lastBolusAmount: Double
        get() = _lastBolusAmount.value
        set(value) {
            _lastBolusAmount.value = value
            preferences.put(AaruyDoubleKey.PumpLastBolusAmount, value)
        }

    private val _lastConnection = MutableStateFlow(0L) // Time in ms!
    val lastConnectionFlow: StateFlow<Long> = _lastConnection
    var lastConnection: Long
        get() = _lastConnection.value
        set(value) {
            _lastConnection.value = value
            preferences.put(AaruyLongKey.PumpLastConnect, value)
        }

    private var _swVersion: String = "" // As reported by pump
    var swVersion: String
        get() = _swVersion
        set(value) {
            _swVersion = value
            preferences.put(AaruyStringKey.PumpSwVersion, value)
        }

    private var _pumpSN = ""
    var pumpSN: String      // 包括aaruy_
        get() = _pumpSN
        set(value) {
            _pumpSN = value
            preferences.put(AaruyStringKey.PumpName, value)
        }

    // bolus status
    private var _bolusStartTime = 0L // Time in ms!
    var bolusStartTime: Long
        get() = _bolusStartTime
        set(value) {
            _bolusStartTime = value
            preferences.put(AaruyLongKey.PumpBolusStartTime, value)
        }
    private var _bolusAmountToBeDelivered = 0.0 // amount to be delivered
    var bolusAmountToBeDelivered: Double
        get() = _bolusAmountToBeDelivered
        set(value) {
            _bolusAmountToBeDelivered = value
            preferences.put(AaruyDoubleKey.PumpBolusAmount, value)
        }
    var bolusProgressLastTimeStamp: Long = 0 // timestamp of last bolus progress message
    var bolusStopped = false // bolus stopped
    var bolusDone = true // Bolus completed or stopped on pump, initialize as true as to don't show bolus on init
    var bolusEndNotSeen = false // The bolus ended while the link was lost, see handleBolusEndNotSeen
    var bolusStopRequested = false // A stop was sent on this link, so the pump leaving the bolus state is the answer to it
    var bolusDelivered = 0.0 // What the pump reported for the running bolus. Not from BolusProgressData: its state can be cleared by the queue.

    private val _bolusAmountDelivered = MutableStateFlow(0.0)
    val bolusAmountDeliveredFlow: StateFlow<Double> = _bolusAmountDelivered

    // temp basal
    var tempBasalStart: Long = 0
    var tempBasalDuration: Long = 0 // in milliseconds
    var tempBasalAbsoluteRate: Double = 0.0

    var isTempBasalInProgress: Boolean
        get() = tempBasalStart != 0L && dateUtil.now() in tempBasalStart..tempBasalStart + tempBasalDuration
        set(isRunning) {
            if (!isRunning) {
                tempBasalStart = 0L
                tempBasalDuration = 0L
            }
        }

    fun temporaryBasalToString(): String {
        if (!isTempBasalInProgress) return ""

        val passedMin = ((min(dateUtil.now(), tempBasalStart + tempBasalDuration) - tempBasalStart) / 60.0 / 1000).roundToInt()
        return tempBasalAbsoluteRate.toString() + "U/h @" +
            dateUtil.timeString(tempBasalStart) +
            " " + passedMin + "/" + T.msecs(tempBasalDuration).mins() + "'"
    }

    fun fromTemporaryBasal(tbr: PumpSync.PumpState.TemporaryBasal?) {
        if (tbr == null) {
            tempBasalStart = 0
            tempBasalDuration = 0
            tempBasalAbsoluteRate = 0.0
        } else {
            tempBasalStart = tbr.timestamp
            tempBasalDuration = tbr.duration
            tempBasalAbsoluteRate = tbr.rate
        }
    }

    // status
    var dailyTotalUnits = 0.0
    var maxDailyTotalUnits = 0

    // Aaruy pump
    private var _maxBasal: Double = 1.5 //最大基础率（U/小时) 0.1~35,默认1.5
    var maxBasal: Double
        get() = _maxBasal
        set(value) {
            _maxBasal = value
            preferences.put(AaruyDoubleKey.PumpMaxBasal, value)
        }
    private var _maxBolus: Double = 10.0 //最大大剂量值（U） 1~25U,默认值10U
    var maxBolus: Double
        get() = _maxBolus
        set(value) {
            _maxBolus = value
            preferences.put(AaruyDoubleKey.PumpMaxBolus, value)
        }
    private var _bolusStep: Int = 2 //大剂量一次注射量(数值就是对应着精度的倍数，从1开始)
    var bolusStep: Int
        get() = _bolusStep
        set(value) {
            _bolusStep = value
            preferences.put(AaruyIntKey.PumpBolusStep, value)
        }
    private var _lowAlert: Double = 20.0 //低药量报警阀值（U），默认为20U
    var lowAlert: Double
        get() = _lowAlert
        set(value) {
            _lowAlert = value
            preferences.put(AaruyDoubleKey.PumpLowAlert, value)
        }
    private var _closeInjectSound: Int = 0 //0:有提示音和报警音 1:关闭提示音，有报警音 2:有提示音，关闭报警音 3：关闭提示音和报警音
    var closeInjectSound: Int
        get() = _closeInjectSound
        set(value) {
            _closeInjectSound = value
            preferences.put(AaruyIntKey.PumpCloseInjectSound, value)
        }
    private var _softNeedleType: Int = 0 //软针长度 0:6mm 1:9mm
    var softNeedleType: Int
        get() = _softNeedleType
        set(value) {
            _softNeedleType = value
            preferences.put(AaruyIntKey.PumpNeedleType, value)
        }
    private var _pumpType: Int = 3//0:AR-B200D 1:AR-B200C 2:AR-B200B 3:AR-B200A
    var pumpType: Int
        get() = _pumpType
        set(value) {
            _pumpType = value
            preferences.put(AaruyIntKey.PumpType, value)
        }

    private var _canSaveHistory: Boolean = false// 是否能保存历史
    var canSaveHistory: Boolean
        get() = _canSaveHistory
        set(value) {
            _canSaveHistory = value
            preferences.put(AaruyBooleanKey.PumpSaveHistory, value)
        }

    private var _lastHistoryTime: Long = 0
    var lastHistoryTime: Long           // 毫秒
        get() = _lastHistoryTime
        set(value) {
            _lastHistoryTime = value
            preferences.put(AaruyLongKey.PumpLastHistoryTime, value)
        }

    private var _lastHistoryDailyTime: Long = 0
    var lastHistoryDailyTime: Long      // 毫秒
        get() = _lastHistoryDailyTime
        set(value) {
            _lastHistoryDailyTime = value
            preferences.put(AaruyLongKey.PumpLastHistoryDailyTime, value)
        }

    private var _alreadyPushBack: Boolean = false
    var alreadyPushBack: Boolean      // 用于主动推杆回退
        get() = _alreadyPushBack
        set(value) {
            _alreadyPushBack = value
            preferences.put(AaruyBooleanKey.PumpAlreadyBack, value)
        }

    fun pumpType(): PumpType = pumpType(AaruyType.fromValue(pumpType))

    fun pumpType(type: AaruyType): PumpType =
        if (type == AaruyType.B200A || type == AaruyType.B200C) {
            PumpType.AARUY_FULL
        }
        else {
            PumpType.AARUY_BASE
        }

    fun addAlarm(alarm: AlarmState) {
        activeAlarms.add(alarm)
        saveActiveAlarms()
    }

    fun removeAlarm(alarm: AlarmState) {
        activeAlarms.remove(alarm)
        saveActiveAlarms()
    }

    fun clearAlarmState() {
        activeAlarms.clear()
        saveActiveAlarms()
    }

    fun alarmStateToString(alarmState: AlarmState): String {
        val stringId = when (alarmState) {
            AlarmState.NONE                             -> R.string.alarm_none
            AlarmState.PUMP_ZERO_RESERVOIR              -> R.string.alarm_zero_dose
            AlarmState.PUMP_LOW_POWER                   -> R.string.alarm_low_power
            AlarmState.PUMP_NO_POWER                    -> R.string.alarm_no_power
            AlarmState.PUMP_LOW_RESERVOIR               -> R.string.alarm_low_dose
            AlarmState.PUMP_NO_RESERVOIR                -> R.string.alarm_no_dose
            AlarmState.PUMP_MOTOR_BLOCK                 -> R.string.alarm_block
            AlarmState.PUMP_MOTOR_FAILED                -> R.string.alarm_error3
            AlarmState.PUMP_MOTOR_BUSY                  -> R.string.alarm_motor_busy
            AlarmState.PUMP_MOTOR_REPEAT                -> R.string.alarm_motor_repeat
            AlarmState.PUMP_MOTOR_MCU_BLOCK             -> R.string.alarm_block2
            AlarmState.PUMP_MOTOR_MCU_ILLEGAL           -> R.string.alarm_error1
            AlarmState.PUMP_MOTOR_MCU_PLUSES_EXTRA      -> R.string.alarm_error2
            AlarmState.PUMP_MOTOR_DESTRUCTIVE_ANOMALY   -> R.string.alarm_error4
            AlarmState.PUMP_MOTOR_FAILED_PLUS           -> R.string.alarm_error3_plus
            AlarmState.PUMP_MOTOR_AUTO_POWEROFF         -> R.string.alarm_auto_poweroff

            AlarmState.PUMP_RUN_BASAL                   -> R.string.alarm_basal_run_fail
            AlarmState.PUMP_RUN_TMP_BASAL               -> R.string.alarm_temp_basal_run_fail
            AlarmState.PUMP_RUN_BOLUS                   -> R.string.alarm_bolus_run_fail
            AlarmState.PUMP_DISCHARGE_LIQUID            -> R.string.alarm_discharge_liquid_fail
            AlarmState.PUMP_PUSH_BACK                   -> R.string.alarm_rewinding_fail
            AlarmState.PUMP_PLEASE_RUN_BASAL            -> R.string.alarm_please_run_basal_first
            AlarmState.PUMP_LOW_BATTERY_RESET           -> R.string.low_battery_not_allow_back
            AlarmState.PUMP_LOW_BATTERY_DISCHARGE       -> R.string.low_battery_not_allow_remove_air
            AlarmState.PUMP_STOP_15M_LATER              -> R.string.alarm_stop_15m_later
            AlarmState.PUMP_STOP_TOO_LONG               -> R.string.alarm_stop_too_long
        }
        return rh.gs(stringId)
    }

    private fun saveActiveAlarms() {
        val alarmsStr = activeAlarms.joinToString(separator = ",") { it.name }
        preferences.put(AaruyStringKey.PumpActiveAlarm, alarmsStr)
    }

    private fun loadActiveAlarms() {
        val alarmsStr = preferences.get(AaruyStringKey.PumpActiveAlarm)
        activeAlarms = if (alarmsStr.isEmpty()) {
            EnumSet.noneOf(AlarmState::class.java)
        } else {
            alarmsStr.split(",")
                .mapNotNull { AlarmState.entries.find { alarm -> alarm.name == it } }
                .let { EnumSet.copyOf(it) }
        }
    }

    private fun newRecordInfo(newRecord: Boolean): String {
        return "${if (newRecord) "**NEW** " else ""}"
    }

    fun dataClassToByteArray(data: BasalInfo): ByteArray {
        val jsonString = Gson().toJson(data)
        return jsonString.toByteArray()
    }

    fun buildAaruyProfileRecord(nsProfile: Profile): BasalInfo {
        // val record = Array(24) { BasalItem(0,0.0) }
        // for (hour in 0..23) {
        //     val value = (100.0 * nsProfile.getBasalTimeFromMidnight((hour * 60 * 60))).roundToInt() / 100.0 + 0.00001
        //     val basalItemSet = BasalItem(hour*2,value)
        //     aapsLogger.debug(LTag.PUMP, "NS basal value for $hour:00 is $value")
        //     record[hour] = basalItemSet
        // }

        val local = nsProfile.getBasalValues().map {
            // Rounding must not turn a rate above zero into zero: the pump would give nothing for that time.
            BasalItem(it.timeAsSeconds / SECONDS_PER_HALF_HOUR, pumpType().determineCorrectBasalSize(it.value).let { rate -> if (rate <= 0.0 && it.value > 0.0) BASAL_STEP else rate })
        }
        val record = AaruyPumpSchedule.toPump(local, AaruyPumpSchedule.shift(dateUtil.now()))
        val totalArea = record.size
        // From the rate the pump really gets. UNVERIFIED if the pump uses this total for anything.
        val injectPerDay = (AaruyPumpSchedule.dailyTotal(record) * 1000.0).roundToInt() / 1000.0
        val basalItems = BasalItems(
            injectPerDay,
            totalArea,
            record
        )

        val basalInfo = BasalInfo(
            basalItems
        )

        aapsLogger.debug(LTag.PUMP, "buildAaruyProfileRecord: injectPerDay: $injectPerDay totalArea: $totalArea")
        return basalInfo
    }

    /**
     * Compares the rates only. The daily total goes to the pump cut to one decimal, so 3.84 comes back as 3.8,
     * and comparing it would send the same profile again at every status check.
     */
    fun isBasalInPump(profile: Profile): Boolean {
        return readBasalInPump()?.basalItems?.basalItemList?.comparable() == buildAaruyProfileRecord(profile).basalItems?.basalItemList?.comparable()
    }

    /**
     * The rates the pump holds, in local time, for the running profile (#2454). The pump keeps a start in half hours.
     * This is true also right after a time zone change, when the pump still holds the old schedule. null before a profile is known.
     */
    fun deliveredBasal(): List<Profile.ProfileValue>? =
        readBasalInPump()?.basalItems?.basalItemList?.let { AaruyPumpSchedule.toLocal(it, AaruyPumpSchedule.shift(dateUtil.now())) }
            ?.map { Profile.ProfileValue(it.startTime * SECONDS_PER_HALF_HOUR, it.basalSpeed) }

    /** The rate the pump holds for this time of day, as the running profile says. A temp basal or a bolus does not change it. 0 when no profile is known. */
    fun baseBasalRateAt(secondsOfDay: Int): Double = deliveredBasal()?.lastOrNull { it.timeAsSeconds <= secondsOfDay }?.value ?: 0.0

    private fun readBasalInPump(): BasalInfo? = try {
        Gson().fromJson(String(actualBasalProfile), BasalInfo::class.java)
    } catch (e: Exception) {
        aapsLogger.error(LTag.PUMP, "The saved pump profile cannot be read", e)
        null
    }

    fun sameRates(first: BasalInfo, second: BasalInfo): Boolean = first.basalItems?.basalItemList?.comparable() == second.basalItems?.basalItemList?.comparable()

    private fun List<BasalItem>.comparable() = map { it.startTime to (it.basalSpeed * 1000).roundToInt() }

    fun handleBolusStatusUpdate(bolusCompleted: Boolean, amountDelivered: Double = 35.0) {
        aapsLogger.debug(LTag.PUMP, "handleBolusStatusUpdate: bolusCompleted: $bolusCompleted amountDelivered: $amountDelivered")
        bolusProgressLastTimeStamp = dateUtil.now()
        // The default of 35.0 is above the pump limit and means "no new amount": keep the last amount the pump reported.
        if (amountDelivered <= 25.0) {
            _bolusAmountDelivered.value = amountDelivered
            bolusDelivered = amountDelivered
            bolusProgressData.updateProgress(PumpInsulin(amountDelivered))
        }
        bolusDone = bolusCompleted
        if (bolusDone) {
            lastBolusAmount = if (amountDelivered <= 25.0) {
                amountDelivered
            } else {
                _bolusAmountDelivered.value
            }
            lastBolusTime = curBolusTime
        }
    }

    /**
     * The pump left the bolus state and the message that says "done" was not seen, because the link was lost.
     * The amount is not known until the history is read.
     */
    fun handleBolusEndNotSeen() {
        bolusEndNotSeen = true
        handleBolusStatusUpdate(true)
    }

    fun handleBasalStatusUpdate(basalValue: Double) {
        // Update aaruy pump state
        _lastBasalRate.value = basalValue
    }

    /**
     * Seen on a real pump: set to 0.05 U it refuses 0.125 U and accepts 0.10 U. The setting reaches the pump only at the
     * next status read, so until then the coarser one is used. A coarser amount is valid on both.
     */
    val bolusStepUnits: Double
        get() = max(bolusStep, if (preferences.get(AaruyIntPreferenceKey.BolusStep) == 1) 2 else 1) * BOLUS_PRECISION_UNITS

    fun handleLoadBasicParam(basicParam: BasicParam): Boolean {
        pumpType = (basicParam.pumpType and 0x7f)
        maxBasal = basicParam.maxBasal
        maxBolus = basicParam.maxBolus
        bolusStep = basicParam.bolusStep
        lowAlert = basicParam.lowAlert
        closeInjectSound = basicParam.closeInjectSound

        return syncPrefParam()
    }

    /** @return false when AAPS has no request that fits this temp basal. */
    suspend fun syncTBRWithPumpId(startTime: Long, rate: Double, duration: Long): Boolean {
        val temporaryBasalInfo = temporaryBasalStorage.findTemporaryBasal(startTime, rate)
        val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = startTime,
            rate = PumpRate(rate),
            duration = duration,
            isAbsolute = true,
            type = temporaryBasalInfo?.type,
            pumpId = startTime,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
        aapsLogger.debug(LTag.PUMPCOMM, "syncTBRWithPumpId: ${newRecordInfo(newRecord)}")
        return temporaryBasalInfo != null
    }

    /** Same record, an earlier start: the same pump ID updates it. */
    suspend fun correctTBRStart(pumpId: Long, startTime: Long, rate: Double, duration: Long, type: PumpSync.TemporaryBasalType) {
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = startTime,
            rate = PumpRate(rate),
            duration = duration,
            isAbsolute = true,
            type = type,
            pumpId = pumpId,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
    }

    /** No insulin while the pump is stopped: a zero temp basal, as the Medtrum driver does. */
    suspend fun syncSuspendWithPumpId(startTime: Long, duration: Long) {
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = startTime,
            rate = PumpRate(0.0),
            duration = duration,
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = startTime,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
    }

    suspend fun announceTempBasalNotFromAaps(rate: String) {
        pumpSync.insertAnnouncement(rh.gs(R.string.aaruy_temp_basal_not_from_aaps, rate), dateUtil.now(), pumpType(), pumpSN)
    }

suspend fun syncStopTBRWithPumpId(endTime: Long) {
        val newRecord = pumpSync.syncStopTemporaryBasalWithPumpId(
            timestamp = endTime,
            endPumpId = endTime,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "syncStopTBRWithPumpId: ${newRecordInfo(newRecord)}"
        )
    }

    suspend fun announceExtendedNotFromAaps() {
        pumpSync.insertAnnouncement(rh.gs(R.string.aaruy_extended_not_from_aaps), dateUtil.now(), pumpType(), pumpSN)
    }

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "Aaruy Pump reset")
        lastConnection = 0
        pumpState = AaruyPumpState.FIRST_INIT
        pumpLastState = AaruyPumpState.FIRST_INIT
    }

    var lastDeliveryEnd: Long
        get() = preferences.get(AaruyLongKey.PumpLastDeliveryEnd)
        set(value) = preferences.put(AaruyLongKey.PumpLastDeliveryEnd, value)

    fun loadVarsFromSP() {
        _pumpState.value = AaruyPumpState.fromInt(preferences.get(AaruyIntKey.PumpState))
        _lastBolusTime.value = preferences.get(AaruyLongKey.PumpLastBolusTime)
        _lastBolusAmount.value = preferences.get(AaruyDoubleKey.PumpLastBolusAmount)
        _lastConnection.value = preferences.get(AaruyLongKey.PumpLastConnect)
        _swVersion = preferences.get(AaruyStringKey.PumpSwVersion)
        _pumpSN =  preferences.get(AaruyStringKey.PumpName)
        _bolusStartTime = preferences.get(AaruyLongKey.PumpBolusStartTime)
        _bolusAmountToBeDelivered = preferences.get(AaruyDoubleKey.PumpBolusAmount)
        _curBolusTime = preferences.get(AaruyLongKey.PumpCurBolusTime)

        // aaruy pump
        // default setting
        bolusStep = 1

        _maxBasal = preferences.get(AaruyDoubleKey.PumpMaxBasal)
        _maxBolus = preferences.get(AaruyDoubleKey.PumpMaxBolus)
        _bolusStep = preferences.get(AaruyIntKey.PumpBolusStep)
        _lowAlert = preferences.get(AaruyDoubleKey.PumpLowAlert)
        _closeInjectSound = preferences.get(AaruyIntKey.PumpCloseInjectSound)
        _softNeedleType = preferences.get(AaruyIntKey.PumpNeedleType)
        _pumpType = preferences.get(AaruyIntKey.PumpType)
        _canSaveHistory = preferences.get(AaruyBooleanKey.PumpSaveHistory)
        _lastHistoryTime = preferences.get(AaruyLongKey.PumpLastHistoryTime)
        _lastHistoryDailyTime = preferences.get(AaruyLongKey.PumpLastHistoryDailyTime)
        _alreadyPushBack = preferences.get(AaruyBooleanKey.PumpAlreadyBack)

        loadActiveAlarms()

        val encodedString = preferences.get(AaruyStringKey.PumpActualBasalProfile)
        try {
            _actualBasalProfile = Base64.decode(encodedString, Base64.DEFAULT)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PUMP, "Error decoding basal profile from SP: $encodedString")
        }
    }

    fun syncPrefParam(): Boolean {
        var isSame = true

        val maxBolusLocal = preferences.get(AaruyDoublePreferenceKey.MaxBolus)
        if (maxBolus != maxBolusLocal) {
            isSame = false
            maxBolus = maxBolusLocal
        }
        aapsLogger.debug("syncPrefParam maxBolus is $maxBolus, maxBolusLocal is $maxBolusLocal")
        val maxBasalLocal = preferences.get(AaruyDoublePreferenceKey.MaxBasal)
        if (maxBasal != maxBasalLocal) {
            isSame = false
            maxBasal = maxBasalLocal
        }
        aapsLogger.debug("syncPrefParam maxBasal is $maxBasal, maxBasalLocal is $maxBasalLocal")
        val lowAlertLocal = preferences.get(AaruyDoublePreferenceKey.LowReservoir)
        if (lowAlert != lowAlertLocal) {
            isSame = false
            lowAlert = lowAlertLocal
        }
        val bolusStepLocal = if (preferences.get(AaruyIntPreferenceKey.BolusStep) == 1) 2 else 1
        if (bolusStep != bolusStepLocal) {
            isSame = false

            bolusStep = bolusStepLocal
        }
        val soundRemindLocal = if (preferences.get(AaruyIntPreferenceKey.SoundPrompt) == 1) 1 else 0
        if(((closeInjectSound and 0x01) xor soundRemindLocal) == 0x01) {
            isSame = false
            closeInjectSound = (closeInjectSound or 0x01) and (0xfffe or soundRemindLocal)
        }
        val soundAlarmLocal = if (preferences.get(AaruyIntPreferenceKey.SoundAlarm) == 1) 1 else 0
        if(((closeInjectSound and 0x02) xor (soundAlarmLocal shl 1)) == 0x02) {
            isSame = false
            closeInjectSound = (closeInjectSound or 0x02) and (0xfffd or (soundAlarmLocal shl 1))
        }
        aapsLogger.debug("syncPrefParam closeInjectSound is $closeInjectSound")
        return isSame
    }

}