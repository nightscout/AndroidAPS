package app.aaps.pump.diaconn

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.joda.time.DateTime
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * State of the Diaconn G8 pump as the driver last read it from the pump, and the state of the bolus that runs now.
 * The answer packets fill most of the fields.
 */
@SingleIn(AppScope::class)
@Inject
class DiaconnG8Pump(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter
) {

    var isPumpLogUploadFailed: Boolean = false

    /** The pump's answer to the last confirm of a setting: 0 = taken. Null until it comes. */
    @Volatile var confirmResult: Int? = null
    var maxBolusePerDay: Double = 0.0
    var pumpIncarnationNum: Int = 65536
    var isPumpVersionGe2_63: Boolean = false // the pump firmware is 2.63 or later
    var isPumpVersionGe3_53: Boolean = false // the pump firmware is 3.53 or later
    var isPumpVersionGe3_58: Boolean = false // BLE permanent connection support
    var insulinWarningGrade: Int = 0
    var insulinWarningProcess: Int = 0
    var insulinWarningRemain: Int = 0
    var batteryWaningGrade: Int = 0
    var batteryWaningProcess: Int = 0
    var batteryWaningRemain: Int = 0
    var injectionBlockType: Int = 0
    var injectionBlockRemainAmount: Double = 0.0
    var injectionBlockProcess: Int = 0
    var injectionBlockGrade: Int = 0
    private val _lastConnectionFlow = MutableStateFlow(0L)
    val lastConnectionFlow: StateFlow<Long> = _lastConnectionFlow.asStateFlow()
    var lastConnection: Long
        get() = lastConnectionFlow.value
        set(value) {
            _lastConnectionFlow.value = value
        }
    var lastSettingsRead: Long = 0
    var mealLimitTime: Int = 0

    // time
    private var pumpTime: Long = 0

    // When the pump answered [pumpTime], by the phone clock. The pump clock goes on as the phone clock does.
    private var pumpTimeReadAt: Long = 0

    /**
     * The pump answered a time that is no real time. A real G8 whose clock was never set showed 00:70 on its screen and
     * answered minute 65 (2026-11-11). It has no time to keep, so the driver sets it.
     */
    var pumpClockImpossible = false
        private set

    fun getPumpTime() = pumpTime

    /** Takes the pump clock as the pump answered it, with [year] in full. */
    fun readPumpClock(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
        this.year = year
        this.month = month
        this.day = day
        this.hour = hour
        this.minute = minute
        this.second = second
        pumpTimeReadAt = dateUtil.now()
        pumpTime = try {
            DateTime(year, month, day, hour, minute, second).millis
        } catch (e: IllegalArgumentException) {
            // Also a local time that a change to summer time skips.
            aapsLogger.warn(LTag.PUMPCOMM, "The pump clock is no real time: $year-$month-$day $hour:$minute:$second (${e.message})")
            0L
        }
        pumpClockImpossible = pumpTime == 0L
    }

    /** Forgets the pump clock before it is read again, so a read with no answer is seen. */
    fun clearPumpClock() {
        pumpTime = 0
        pumpClockImpossible = false
    }

    /** How far the pump clock is ahead of the phone, in seconds, or null when the pump has no real time. */
    fun pumpClockOffsetSeconds(): Long? = if (pumpTime == 0L) null else (pumpTime - pumpTimeReadAt) / 1000L

    // Status
    var pumpSuspended = false
    var dailyTotalUnits = 0.0
    var maxDailyTotalUnits = 0
    // Fixed on the G8 and the same as its pump type. Not read from the pump, only shown in the overview.
    val bolusStep = 0.01
    val basalStep = 0.01
    var iob = 0.0

    var bolusBlocked = false

    private val _lastBolusTimeFlow = MutableStateFlow<Long?>(null)
    val lastBolusTimeFlow: StateFlow<Long?> = _lastBolusTimeFlow.asStateFlow()
    var lastBolusTime: Long?
        get() = lastBolusTimeFlow.value
        set(value) {
            _lastBolusTimeFlow.value = value
        }

    private val _lastBolusAmountFlow = MutableStateFlow<Double?>(null)
    val lastBolusAmountFlow: StateFlow<Double?> = _lastBolusAmountFlow.asStateFlow()
    var lastBolusAmount: Double?
        get() = lastBolusAmountFlow.value
        set(value) {
            _lastBolusAmountFlow.value = value
        }

    /*
     * TEMP BASALS
     */
    /** When the user last started the insulin again on the pump after a stop, from the pump history. 0 when not known. */
    var lastPumpRestart: Long = 0
    var tempBasalStart: Long = 0
    var tempBasalDuration: Long = 0 // in milliseconds
    var tempBasalAbsoluteRate: Double = 0.0

    var isTempBasalInProgress: Boolean
        get() = tempBasalStart != 0L && dateUtil.now() in tempBasalStart..tempBasalStart + tempBasalDuration
        set(isRunning) {
            if (isRunning) throw IllegalArgumentException("Use to cancel TBR only")
            else {
                tempBasalStart = 0L
                tempBasalDuration = 0L
                tempBasalAbsoluteRate = 0.0
            }
        }
    val tempBasalRemainingMin: Int
        get() = max(T.msecs(tempBasalStart + tempBasalDuration - dateUtil.now()).mins().toInt(), 0)

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

    /**
     * Sync TBR state from pump response fields (tbStatus, tbTime, tbInjectRateRatio, tbElapsedTime)
     * This is more reliable than pumpSync.expectedPumpState() which may have timing issues
     * Note: tbTime is in 15-minute units (e.g., tbTime=2 means 30 minutes)
     *       tbElapsedTime is in minutes
     */
    fun syncTempBasalFromPump() {
        if (tbStatus == 1) { // TBR is running
            // Calculate start time from elapsed time (tbElapsedTime is in minutes)
            tempBasalStart = dateUtil.now() - T.mins(tbElapsedTime.toLong()).msecs()
            // Duration in milliseconds (tbTime is in 15-minute units)
            tempBasalDuration = T.mins((tbTime * 15).toLong()).msecs()
            // Calculate absolute rate from tbInjectRateRatio
            tempBasalAbsoluteRate = if (tbInjectRateRatio >= 50000) {
                // Percentage mode: 50000 = 0%, 50100 = 100%, 50250 = 250%
                val percent = tbInjectRateRatio - 50000
                baseAmount * percent / 100.0
            } else {
                // Absolute mode: 1000 = 0.00 U/h, 1025 = 0.25 U/h, 1600 = 6.00 U/h
                (tbInjectRateRatio - 1000) / 100.0
            }
            aapsLogger.debug(LTag.PUMP, "syncTempBasalFromPump: running, start=$tempBasalStart, duration=${T.msecs(tempBasalDuration).mins()}min (tbTime=$tbTime), rate=$tempBasalAbsoluteRate U/h")
        } else {
            // TBR is not running - clear state
            tempBasalStart = 0
            tempBasalDuration = 0
            tempBasalAbsoluteRate = 0.0
            aapsLogger.debug(LTag.PUMP, "syncTempBasalFromPump: not running (tbStatus=$tbStatus), state cleared")
        }
    }

    /*
    * EXTENDED BOLUSES
    */
    var extendedBolusStart: Long = 0
    var extendedBolusDuration: Long = 0
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
    val extendedBolusDurationInMinutes: Int
        get() = T.msecs(extendedBolusDuration).mins().toInt()

    var extendedBolusAbsoluteRate: Double
        get() = extendedBolusAmount * T.hours(1).msecs() / extendedBolusDuration
        set(rate) {
            extendedBolusAmount = rate * extendedBolusDuration / T.hours(1).msecs()
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

    /**
     * Sync Extended Bolus state from pump response fields (squareStatus, squareTime, squareInjTime, squareAmount)
     * This is more reliable than pumpSync.expectedPumpState() which may have timing issues
     */
    fun syncExtendedBolusFromPump() {
        if (squareStatus == 1) { // EB is running
            // Calculate start time from elapsed time
            extendedBolusStart = dateUtil.now() - T.mins(squareInjTime.toLong()).msecs()
            // Duration in milliseconds
            extendedBolusDuration = T.mins(squareTime.toLong()).msecs()
            // Amount
            extendedBolusAmount = squareAmount
            aapsLogger.debug(LTag.PUMP, "syncExtendedBolusFromPump: running, start=$extendedBolusStart, duration=${T.msecs(extendedBolusDuration).mins()}min, amount=$extendedBolusAmount U")
        } else {
            // EB is not running - clear state
            extendedBolusStart = 0
            extendedBolusDuration = 0
            extendedBolusAmount = 0.0
            aapsLogger.debug(LTag.PUMP, "syncExtendedBolusFromPump: not running, state cleared")
        }
    }

    // Profile
    var activeProfile = 0
    var pumpProfiles: Array<Array<Double>>? = null

    //Limits
    var maxBolus = 0.0
    var maxBasal = 0.0

    // User settings
    var setUserOptionType = 0 // ALARM:0, LCD:1, LANG:2, BOLUS_SPEED:3
    var beepAndAlarm = 0
    var alarmIntensity = 0
    var lcdOnTimeSec = 0
    var selectedLanguage = 0
    var bolusSpeed = 0

    var resultErrorCode: Int = 0 // last start bolus erroCode

    // Bolus settings
    var bolusingDetailedBolusInfo: DetailedBolusInfo? = null // actually delivered treatment
    var bolusProgressLastTimeStamp: Long = 0 // timestamp of last bolus progress message
    // The bolus end report sets these on the Bluetooth thread, while the bolus waits for them on another one.
    @Volatile var bolusStopped = false // bolus finished
    @Volatile var bolusStopForced = false // bolus forced to stop by user
    @Volatile var bolusDone = false // the end report came

    /** The end report of the last bolus: the pump gave all it was set to give. */
    @Volatile var bolusGivenInFull = false

    /**
     * How many confirm reports (0xE8) came from the pump. It sends one when it has carried out a setting it confirmed;
     * see `DiaconnG8Service.awaitCarriedOut`. Written only on the Bluetooth thread.
     */
    @Volatile var confirmReports = 0

    /** The pump stopped the last bolus itself, as at an occlusion: it gave less, and the user did not stop it. */
    val bolusStoppedByPump: Boolean get() = bolusDone && !bolusGivenInFull && !bolusStopForced

    val pumpUid: String
        get() = "$country-$productType-$makeYear-${makeMonth.toString().padStart(2, '0')}-${makeDay.toString().padStart(2, '0')}-${lotNo.toString().padStart(3, '0')}-${
            serialNo.toString().padStart(5, '0')
        }"

    val pumpVersion: String
        get() = "$majorVersion.$minorVersion"

    fun buildDiaconnG8ProfileRecord(nsProfile: Profile): Array<Double> {
        val record = Array(24) { 0.0 }
        for (hour in 0..23) {
            //Some values get truncated to the next lower one.
            // -> round them to two decimals and make sure we are a small delta larger (that will get truncated)
            val value = (100.0 * nsProfile.getBasalTimeFromMidnight((hour * 60 * 60))).roundToInt() / 100.0 + 0.00001
            aapsLogger.debug(LTag.PUMP, "NS basal value for $hour:00 is $value")
            record[hour] = value
            //aapsLogger.debug(LTag.PUMP, "NS basal value * 100 for $hour:00 is $value")
        }
        return record
    }

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "Diaconn G8 Pump reset")
        lastConnection = 0
        lastSettingsRead = 0
        lastBolusTime = null
        lastBolusAmount = null
        clearPumpClock()
    }

    // G8 pump
    var result: Int = 0 // result of the inquiry

    // 1. pump setting info
    private val _systemRemainInsulinFlow = MutableStateFlow(0.0)
    val systemRemainInsulinFlow: StateFlow<Double> = _systemRemainInsulinFlow.asStateFlow()
    var systemRemainInsulin: Double // insulin left in the reservoir
        get() = systemRemainInsulinFlow.value
        set(value) {
            _systemRemainInsulinFlow.value = value
        }

    private val _systemRemainBatteryFlow = MutableStateFlow<Int?>(null)
    val systemRemainBatteryFlow: StateFlow<Int?> = _systemRemainBatteryFlow.asStateFlow()
    var systemRemainBattery: Int? // battery left (0~100%)
        get() = systemRemainBatteryFlow.value
        set(value) {
            _systemRemainBatteryFlow.value = value
        }
    var systemBasePattern = 0 // basal pattern (0=none, 1=basic, 2=life 1, 3=life 2, 4=life 3, 5=doctor 1, 6=doctor 2)
    var systemTbStatus = 0 // temp basal state (1=running, 2=released)
    var systemInjectionMealStatus = 0 // meal bolus state (1=delivering, 2=not delivering)
    var systemInjectionSnackStatus = 0 // snack bolus state (1=delivering, 2=not delivering)
    var systemInjectionSquareStatue = 0 // square (extended) bolus state (1=delivering, 2=not delivering)
    var systemInjectionDualStatus = 0 // dual bolus state (1=delivering, 2=not delivering)

    // 2. basal injection suspend status (1:stop, 2:release)
    var basePauseStatus = 0 // state (1=stopped, 2=released)

    // 3. Pump time
    var year = 0 // year, in full (the pump sends 18~99)
    var month = 0 // month (1~12)
    var day = 0 // day (1~31)
    var hour = 0 // hour (0~23)
    var minute = 0 // minute (0~59)
    var second = 0 // second (0~59)

    // 4. pump system info
    var country = 0 // country of manufacture (K, C), ASCII
    var productType = 0 // product type (A ~ Z), ASCII
    var makeYear = 0 // year of manufacture
    var makeMonth = 0 // month of manufacture
    var makeDay = 0 // day of manufacture
    var lotNo = 0 // LOT NO
    var serialNo = 0 // SERIAL NO
    var majorVersion = 0 // major version
    var minorVersion = 0 // minor version

    // 5. pump log status
    var pumpLastLogNum = 0 // number of the last saved log (0~9999)
    var pumpWrappingCount = 0 // wrapping count (0~255)
    var apslastLogNum = 0 // number of the last log that the app has handled
    var apsWrappingCount = 0 // wrapping count of the last log that the app has handled
    var isPlatformUploadStarted = false // true while the pump logs are uploaded to the Diaconn cloud

    // 6. bolus speed status.
    var speed = 0 // bolus speed (1 ~ 8)

    /** The seconds the pump takes for one unit at its bolus [speed], 12 when the speed is not known. */
    val secondsPerUnit: Int
        get() = when (speed) {
            1    -> 60
            2    -> 30
            3    -> 20
            4    -> 15
            6    -> 10
            7    -> 9
            8    -> 8
            else -> 12
        }

    /**
     * The time a bolus of [units] started, from the time the pump logged it. Real G8 fw 2.50 (2026-11-11): the pump logs
     * a bolus when it ends. A 1 U bolus confirmed 21:10:58 was logged 21:11:20, and one the user stopped was logged at the
     * stop. Stored at the log time, a bolus longer than a minute no longer matched the bolus AAPS asked for, so it lost
     * its type (an SMB, a bolus of the wizard), and its insulin counted from its end. Seen for the normal bolus; the meal
     * bolus and the first part of a dual bolus are given the same way, but were not seen on a real pump.
     */
    fun bolusStartOf(loggedAt: Long, units: Double): Long = loggedAt - (max(units, 0.0) * secondsPerUnit * 1000).toLong()
    var maxBasalPerHours = 0.0

    // 7. Tempbasal status
    var tbStatus = 0 // temp basal state (1=running, 2=not running)
    var tbTime = 0 // temp basal duration, in 15-minute steps
    var tbInjectRateRatio = 0 // temp basal amount or ratio: 1000 (0.00 U) ~ 1600 (6.00 U), 50000 (0%) ~ 50250 (250%). 50000 or more is a ratio
    var tbElapsedTime = 0 // temp basal elapsed time (0~1425 min)

    // 8. Basal status
    var baseStatus = 0 // delivery state
    var baseHour = 0 // hour of the current delivery (0~23)
    var baseAmount = 0.0 // set amount (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseInjAmount = 0.0 // amount delivered so far (pump sends amount*100: 23.25->2325, 15.2->1520)

    // 9. meal bolus status
    var mealKind = 0 // meal kind (1=breakfast, 2=lunch, 3=dinner)
    var mealStartTime = 0 // meal bolus start time (time_t)
    var mealStatus = 0 // delivery state (1=delivering, 2=not delivering)
    var mealAmount = 0.0 // set amount (pump sends amount*100: 23.25->2325, 15.2->1520)
    var mealInjAmount = 0.0 // amount delivered so far (pump sends amount*100: 23.25->2325, 15.2->1520)
    var mealSpeed = 0 // delivery speed (1~8)

    // 10. snack bolus status
    var snackStatus = 0 // delivery state (1=delivering, 2=not delivering)
    var snackAmount = 0.0 // set amount (pump sends amount*100: 23.25->2325, 15.2->1520)
    var snackInjAmount = 0.0 // amount delivered so far (pump sends amount*100: 23.25->2325, 15.2->1520)
    var snackSpeed = 0 // delivery speed (1~8)

    // 11. square(extended) bolus status
    var squareStatus = 0 // delivery state
    var squareTime = 0 // set duration (10~300 min)
    var squareInjTime = 0 // elapsed time (10~300 min)
    var squareAmount = 0.0 // set amount
    var squareInjAmount = 0.0 // amount delivered so far

    // 12. dual bolus status
    var dualStatus = 0 // delivery state
    var dualAmount = 0.0 // set amount of the normal part
    var dualInjAmount = 0.0 // amount delivered of the normal part
    var dualSquareTime = 0 // set duration of the square part (10~300 min)
    var dualInjSquareTime = 0 // elapsed time of the square part (10~300 min)
    var dualSquareAmount = 0.0 // set amount of the square part
    var dualInjSquareAmount = 0.0 // amount delivered of the square part

    // 13. last injection  status
    var recentKind1 = 0 // kind of the last bolus (1=meal, 2=snack, 3=square, 4=dual)
    var recentTime1 = 0 // time of the last bolus
    var recentAmount1 = 0.0 // amount of the last bolus
    var recentKind2 = 0 // kind of the bolus before the last one (1=meal, 2=snack, 3=square, 4=dual)
    var recentTime2 = 0 // time of the bolus before the last one
    var recentAmount2 = 0.0 // amount of the bolus before the last one

    // 14. daily injection status
    var todayBaseAmount = 0.0 // total basal today
    var todayMealAmount = 0.0 // total meal bolus today
    var todaySnackAmount = 0.0 // total of the other boluses today (snack, square, dual)

    // 15. meal setting status
    var morningHour = 0 // breakfast start hour (0~23)
    var morningAmount = 0.0 // breakfast bolus amount
    var lunchHour = 0 // lunch start hour (0~23)
    var lunchAmount = 0.0 // lunch bolus amount
    var dinnerHour = 0 // dinner start hour (0~23)
    var dinnerAmount = 0.0 // dinner bolus amount

    // 16. basal injection status at this hour
    var currentBasePattern = 0 // pattern kind (1=basic, 2=life 1, 3=life 2, 4=life 3, 5=doctor 1, 6=doctor 2)
    var currentBaseHour = 0 // hour of the current delivery (0~23)
    var currentBaseTbBeforeAmount = 0.0 // basal amount of this hour before the temp basal is applied. Not counted: the amount blocked by an occlusion, the amount not delivered while basal was stopped, the amount not delivered because of a reset (47.5=4750)
    var currentBaseTbAfterAmount = 0.0 // basal amount of this hour after the temp basal is applied. Not counted: the amount blocked by an occlusion, the amount not delivered while basal was stopped, the amount not delivered because of a reset (47.5=4750)

    // 17. saved basal pattern status
    var baseAmount1 = 0.00// basal amount 1 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount2 = 0.0// basal amount 2 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount3 = 0.0 // basal amount 3 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount4 = 0.0 // basal amount 4 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount5 = 0.0 // basal amount 5 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount6 = 0.0 // basal amount 6 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount7 = 0.0 // basal amount 7 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount8 = 0.0 // basal amount 8 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount9 = 0.0 // basal amount 9 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount10 = 0.0 // basal amount 10 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount11 = 0.0 // basal amount 11 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount12 = 0.0 // basal amount 12 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount13 = 0.0 // basal amount 13 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount14 = 0.0 // basal amount 14 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount15 = 0.0 // basal amount 15 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount16 = 0.0 // basal amount 16 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount17 = 0.0 // basal amount 17 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount18 = 0.0 // basal amount 18 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount19 = 0.0 // basal amount 19 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount20 = 0.0 // basal amount 20 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount21 = 0.0 // basal amount 21 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount22 = 0.0 // basal amount 22 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount23 = 0.0 // basal amount 23 (pump sends amount*100: 23.25->2325, 15.2->1520)
    var baseAmount24 = 0.0 // basal amount 24 (pump sends amount*100: 23.25->2325, 15.2->1520)

    var otpNumber = 0

    var bolusingSetAmount = 0.0
    var bolusingInjAmount = 0.0
    var bolusingSpeed = 0
    var bolusingInjProgress = 0

    companion object {

        // User settings
        const val ALARM = 0
        const val LCD = 1
        const val LANG = 2
        const val BOLUS_SPEED = 3
    }

}
