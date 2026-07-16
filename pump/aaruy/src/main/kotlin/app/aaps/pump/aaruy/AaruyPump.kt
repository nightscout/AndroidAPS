package app.aaps.pump.aaruy

import android.util.Base64
import androidx.lifecycle.MutableLiveData
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import com.aaruy.mylibrary.open.data.BasalInfo
import com.aaruy.mylibrary.open.data.BasalItem
import com.aaruy.mylibrary.open.data.BasalItems
import com.aaruy.mylibrary.open.data.BasicParam
import com.google.gson.Gson
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AaruyType
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.common.util.AaruyConst
import app.aaps.pump.aaruy.keys.AaruyBooleanKey
import app.aaps.pump.aaruy.keys.AaruyDoubleKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyLongKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class AaruyPump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val decimalFormatter: DecimalFormatter
)  {

    companion object {

        const val FAKE_TBR_LENGTH = 1440L
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
    val errCode = MutableLiveData<Int>()

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

    private var _lastBolusTime = 0L // Time in ms!
    var lastBolusTime: Long
        get() = _lastBolusTime
        set(value) {
            _lastBolusTime = value
            preferences.put(AaruyLongKey.PumpLastBolusTime, value)
        }

    private var _lastBolusAmount = 0.0
    var lastBolusAmount: Double
        get() = _lastBolusAmount
        set(value) {
            _lastBolusAmount = value
            preferences.put(AaruyDoubleKey.PumpLastBolusAmount, value)
        }

    private var _lastConnection = 0L // Time in ms!
    var lastConnection: Long
        get() = _lastConnection
        set(value) {
            _lastConnection = value
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

    // extended bolus
    var extendedBolusStart: Long = 0
    var extendedBolusDuration: Long = 0     // ms 毫秒
    var extendedBolusAmount = 0.0

    var isExtendedInProgress: Boolean
        get() = extendedBolusStart != 0L && dateUtil.now() in extendedBolusStart..extendedBolusStart + extendedBolusDuration
        set(isRunning) {
            if (isRunning) throw IllegalArgumentException("Use to cancel EB only")
            else {
                extendedBolusStart = 0L
                extendedBolusDuration = 0L
                extendedBolusAmount = 0.0
            }
        }
    val extendedBolusPassedMinutes: Int
        get() = T.msecs(max(0, dateUtil.now() - extendedBolusStart)).mins().toInt()
    val extendedBolusRemainingMinutes: Int
        get() = max(T.msecs(extendedBolusStart + extendedBolusDuration - dateUtil.now()).mins().toInt(), 0)
    private val extendedBolusDurationInMinutes: Int
        get() = T.msecs(extendedBolusDuration).mins().toInt()

    var extendedBolusAbsoluteRate: Double
        get() = extendedBolusAmount * T.hours(1).msecs() / extendedBolusDuration
        set(rate) {
            extendedBolusAmount = rate * extendedBolusDuration / T.hours(1).msecs()
        }

    fun extendedBolusToString(): String {
        if (!isExtendedInProgress) return ""
        //return "E "+ decimalFormatter.to2Decimal(extendedBolusDeliveredSoFar) +"/" + decimalFormatter.to2Decimal(extendedBolusAbsoluteRate) + "U/h @" +
        //     " " + extendedBolusPassedMinutes + "/" + extendedBolusMinutes + "'"
        return "E " + decimalFormatter.to2Decimal(extendedBolusAbsoluteRate) + "U/h @" +
            dateUtil.timeString(extendedBolusStart) +
            " " + extendedBolusPassedMinutes + "/" + extendedBolusDurationInMinutes + "'"
    }

    fun fromExtendedBolus(eb: PumpSync.PumpState.ExtendedBolus?) {
        if (eb == null) {
            extendedBolusStart = 0
            extendedBolusDuration = 0
            extendedBolusAmount = 0.0
        } else {
            extendedBolusStart = eb.timestamp
            extendedBolusDuration = eb.duration
            extendedBolusAmount = eb.amount
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

        val list = nsProfile.getBasalValues()
        var injectPerDay: Double = 0.0
        val totalArea = list.size
        var timeCal: Int = 0
        val record = Array(totalArea){ BasalItem(0,0.0) }

        for((index, item) in list.withIndex()) {
            timeCal = if (index == totalArea - 1) {
                24 - item.timeAsSeconds/3600
            } else {
                list[index+1].timeAsSeconds/3600 - item.timeAsSeconds/3600
            }
            injectPerDay += (item.value * timeCal + 0.00001)

            val basalItemSet = BasalItem(0,0.0)
            basalItemSet.startTime = item.timeAsSeconds/3600*2
            basalItemSet.basalSpeed = pumpType().determineCorrectBasalSize(item.value)
            record[index] = basalItemSet
            aapsLogger.debug(LTag.PUMP, "buildAaruyProfileRecord: item.value: ${item.value} " +
                "item.timeAsSeconds: ${item.timeAsSeconds}, timeCal is $timeCal, basalItemSet.basalSpeed is ${basalItemSet.basalSpeed}")
        }

        injectPerDay = ((injectPerDay*1000.0).roundToInt()) / 1000.0
        val basalItems = BasalItems(
            injectPerDay,
            totalArea,
            record.toList()
        )

        val basalInfo = BasalInfo(
            basalItems
        )

        aapsLogger.debug(LTag.PUMP, "buildAaruyProfileRecord: injectPerDay: $injectPerDay totalArea: $totalArea")
        return basalInfo
    }

    fun buildAaruyProfileArray(profile: Profile): ByteArray {
        val basalInfo = buildAaruyProfileRecord(profile)
        val byteArray = dataClassToByteArray(basalInfo)
        return byteArray
    }

    fun handleBolusStatusUpdate(bolusType: Int, bolusCompleted: Boolean, amountDelivered: Double = 35.0) {
        aapsLogger.debug(LTag.PUMP, "handleBolusStatusUpdate: bolusType: $bolusType bolusCompleted: $bolusCompleted amountDelivered: $amountDelivered")
        bolusProgressLastTimeStamp = dateUtil.now()
        if (amountDelivered <= 25.0) {
            _bolusAmountDelivered.value = amountDelivered
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

        BolusProgressData.delivered = amountDelivered
    }

    fun handleBasalStatusUpdate(basalValue: Double) {
        // Update aaruy pump state
        _lastBasalRate.value = basalValue
    }

    fun handleLoadBasicParam(basicParam: BasicParam): Boolean {
        pumpType = (basicParam.pumpType and 0x7f)
        maxBasal = basicParam.maxBasal
        maxBolus = basicParam.maxBolus
        bolusStep = basicParam.bolusStep
        lowAlert = basicParam.lowAlert
        closeInjectSound = basicParam.closeInjectSound

        return syncPrefParam()
    }

    fun syncTBRWithPumpId(startTime: Long, rate: Double, duration: Long) {
        val temporaryBasalInfo = temporaryBasalStorage.findTemporaryBasal(startTime, rate)
        val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = startTime,
            rate = rate,
            duration = duration,
            isAbsolute = true,
            type = temporaryBasalInfo?.type,
            pumpId = startTime,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "syncTBRWithPumpId: ${newRecordInfo(newRecord)}"
        )
    }

    fun syncExtendedWithPumpId(startTime: Long, amount: Double, duration: Long) {
        val newRecord = pumpSync.syncExtendedBolusWithPumpId(
            timestamp = startTime,
            amount = amount,
            duration = duration,
            isEmulatingTB = false,
            pumpId = startTime,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "syncExtendedWithPumpId: ${newRecordInfo(newRecord)}"
        )
    }

    fun syncStopTBRWithPumpId(endTime: Long) {
        val newRecord = pumpSync.syncStopTemporaryBasalWithPumpId(
            timestamp = endTime,
            endPumpId = endTime,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "syncExtendedWithPumpId: ${newRecordInfo(newRecord)}"
        )
    }

    fun syncStopExtendedWithPumpId(endTime: Long) {
        val newRecord = pumpSync.syncStopExtendedBolusWithPumpId(
            timestamp = endTime,
            endPumpId = endTime,
            pumpType = pumpType(),
            pumpSerial = pumpSN
        )
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "syncExtendedWithPumpId: ${newRecordInfo(newRecord)}"
        )
    }

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "Aaruy Pump reset")
        lastConnection = 0
        pumpState = AaruyPumpState.FIRST_INIT
        pumpLastState = AaruyPumpState.FIRST_INIT
    }

    fun loadVarsFromSP() {
        _pumpState.value = AaruyPumpState.fromInt(preferences.get(AaruyIntKey.PumpState))
        _lastBolusTime = preferences.get(AaruyLongKey.PumpLastBolusTime)
        _lastBolusAmount = preferences.get(AaruyDoubleKey.PumpLastBolusAmount)
        _lastConnection = preferences.get(AaruyLongKey.PumpLastConnect)
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

        val maxBolusLocal = checkParameterValue(AaruyStringKey.PumpPrefMaxBolus, "10.0", 10.0, 25.0)
        if (maxBolus != maxBolusLocal) {
            isSame = false
            maxBolus = maxBolusLocal
        }
        aapsLogger.debug("syncPrefParam maxBolus is $maxBolus, maxBolusLocal is $maxBolusLocal")
        val maxBasalLocal = checkParameterValue(AaruyStringKey.PumpPrefMaxBasal, "2", 2.0, 35.0)
        if (maxBasal != maxBasalLocal) {
            isSame = false
            maxBasal = maxBasalLocal
        }
        aapsLogger.debug("syncPrefParam maxBasal is $maxBasal, maxBasalLocal is $maxBasalLocal")
        val lowAlertLocal = checkParameterValue(AaruyStringKey.PumpPrefLowAlert, "20.0", 20.0, 50.0)
        if (lowAlert != lowAlertLocal) {
            isSame = false
            lowAlert = lowAlertLocal
        }
        val bolusStepLocal = getBolusIncFromString(AaruyIntKey.PumpPrefBolusStep, "0.05")
        if (bolusStep != bolusStepLocal) {
            isSame = false

            bolusStep = bolusStepLocal
        }
        val soundRemindLocal = getOnOrOffFromString(AaruyIntKey.PumpPrefSoundRemind, rh.gs(R.string.setting_aaruy_on))
        if(((closeInjectSound and 0x01) xor soundRemindLocal) == 0x01) {
            isSame = false
            closeInjectSound = (closeInjectSound or 0x01) and (0xfffe or soundRemindLocal)
        }
        val soundAlarmLocal = getOnOrOffFromString(AaruyIntKey.PumpPrefSoundAlarm, rh.gs(R.string.setting_aaruy_on))
        if(((closeInjectSound and 0x02) xor (soundAlarmLocal shl 1)) == 0x02) {
            isSame = false
            closeInjectSound = (closeInjectSound or 0x02) and (0xfffd or (soundAlarmLocal shl 1))
        }
        aapsLogger.debug("syncPrefParam closeInjectSound is $closeInjectSound")
        return isSame
    }

    private fun checkParameterValue(key: AaruyStringKey, defaultValue: String, defaultValueDouble: Double, maxValueDouble: Double): Double {
        var valueDouble: Double
        val value = preferences.get(key)
        valueDouble = try {
            value.toDouble()
        } catch (ex: Exception) {
            aapsLogger.error("Error parsing setting: %s, value found %s", key, value)
            defaultValueDouble
        }
        if (valueDouble > maxValueDouble) {
            preferences.put(key, maxValueDouble.toString())
            valueDouble = maxValueDouble
        }
        return valueDouble
    }

    private fun getBolusIncFromString(key: AaruyIntKey, defaultValue: String): Int {
        var valueInt: Int = 1
        val value = preferences.get(key)
        if (value == 1)
            valueInt = 2
        return valueInt
    }

    private fun getOnOrOffFromString(key: AaruyIntKey, defaultValue: String): Int {
        var valueInt: Int = 0
        val value = preferences.get(key)
        if (value == 1)
            valueInt = 1
        return valueInt
    }
}