package app.aaps.pump.diaconn

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class DiaconnG8Pump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter
) {

    var isPumpLogUploadFailed: Boolean = false

    //var bleResultInfo: Pair<Int?, Boolean> = Pair(null, false)
    var bolusConfirmMessage: Byte = 0

    var isReadyToBolus: Boolean = false
    var maxBolusePerDay: Double = 0.0
    var pumpIncarnationNum: Int = 65536
    var isPumpVersionGe2_63: Boolean = false // is pumpVersion higher then 2.63
    var isPumpVersionGe3_53: Boolean = false // is pumpVersion higher then 3.42
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
    var lastConnection: Long = 0
    var lastSettingsRead: Long = 0
    var mealLimitTime: Int = 0

    // time
    private var pumpTime: Long = 0

    fun setPumpTime(value: Long) {
        pumpTime = value
    }

    fun getPumpTime() = pumpTime

    // Status
    var pumpSuspended = false
    var dailyTotalUnits = 0.0
    var maxDailyTotalUnits = 0
    var bolusStep = 0.01
    var basalStep = 0.01
    var iob = 0.0

    var bolusBlocked = false
    var lastBolusTime: Long = 0
    var lastBolusAmount = 0.0

    /*
     * TEMP BASALS
     */
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
    var bolusStopped = false // bolus finished
    var bolusStopForced = false // bolus forced to stop by user
    var bolusDone = false // success end

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
    }

    // G8 pump
    var result: Int = 0 // 조회결과

    // 1. pump setting info
    var systemRemainInsulin = 0.0 // 인슐린 잔량
    var systemRemainBattery: Int? = null // 배터리 잔량(0~100%)
    var systemBasePattern = 0 // 기저주입 패턴(0=없음, 1=기본, 2=생활1, 3=생활2, 4=생활3, 5=닥터1, 6=닥터2)
    var systemTbStatus = 0 // 임시기저 상태(1=임시기저 중, 2=임시기저 해제)
    var systemInjectionMealStatus = 0 // 식사주입 상태(1=주입중, 2=주입상태아님)
    var systemInjectionSnackStatus = 0 // 일반간식 주입 상태(1=주입중, 2=주입상태아님)
    var systemInjectionSquareStatue = 0 // 스퀘어회식 주입 상태(1=주입중, 2=주입상태아님)
    var systemInjectionDualStatus = 0 // 더블회식 주입 상태(1=주입중, 2=주입상태아님)

    // 2. basal injection suspend status (1:stop, 2:release)
    var basePauseStatus = 0 // 상태(1=정지,2=해제)

    // 3. Pump time
    var year = 0 // 년 (18~99)
    var month = 0 // 월 (1~12)
    var day = 0 // 일 (1~31)
    var hour = 0 // 시 (0~23)
    var minute = 0 // 분 (0~59)
    var second = 0 // 초 (0~59)

    // 4. pump system info
    var country = 0 // 생산국(K, C), ASCII
    var productType = 0 // 제품종류(A ~ Z), ASCII
    var makeYear = 0 // 제조년
    var makeMonth = 0 // 제조월
    var makeDay = 0 // 제조일
    var lotNo = 0 // LOT NO
    var serialNo = 0 // SERIAL NO
    var majorVersion = 0 // Major 버전
    var minorVersion = 0 // Minor 버전

    // 5. pump log status
    var pumpLastLogNum = 0 // 마지막 저장 로그 번호(0~9999)
    var pumpWrappingCount = 0 // wrapping 카운트(0~255)
    var apslastLogNum = 0 // 앱에서 처리한 마지막 로그 번호.
    var apsWrappingCount = 0 // 앱에서 처리한 마지막 로그 번호.
    var isPlatformUploadStarted = false // 플랫폼 로그 동기화 진행 여부

    // 6. bolus speed status.
    var speed = 0 // 주입 속도(1 ~ 8)
    var maxBasalPerHours = 0.0

    // 7. Tempbasal status
    var tbStatus = 0 // 임시기저 상태 (1 : running, 2:not  running )
    var tbTime = 0 // 임시기저 시간
    var tbInjectRateRatio = 0 // 임시기저 주입량/률  1000(0.00U)~1600(6.00U), 50000(0%)~50250(250%), 50000이상이면 주입률로 판정
    var tbElapsedTime = 0 // 임시기저 경과 시간(0~1425분)

    // 8. Basal status
    var baseStatus = 0 // 주입상태
    var baseHour = 0 // 현재주입시간(0~23)
    var baseAmount = 0.0 // 주입설정량(량*100, 23.25->2325, 15.2->1520)
    var baseInjAmount = 0.0 // 현재주입량(량*100, 23.25->2325, 15.2->1520)

    // 9. meal bolus status
    var mealKind = 0 // 주입종류(1=아침,2=점심,3=저녁)
    var mealStartTime = 0 // 식사주입 시작시간(time_t)
    var mealStatus = 0 // 주입상태(1=주입중,2=주입상태아님)
    var mealAmount = 0.0 // 주입설정량(량*100, 23.25->2325, 15.2->1520)
    var mealInjAmount = 0.0 // 현재주입량(량*100, 23.25->2325, 15.2->1520)
    var mealSpeed = 0 // 주입속도(1~8)

    // 10. snack bolus status
    var snackStatus = 0 // 주입상태(1=주입중,2=주입상태아님)
    var snackAmount = 0.0 // 주입설정량(량*100, 23.25->2325, 15.2->1520)
    var snackInjAmount = 0.0 // 현재주입량(량*100, 23.25->2325, 15.2->1520)
    var snackSpeed = 0 // 주입속도(1~8)

    // 11. square(extended) bolus status
    var squareStatus = 0 // 주입상태
    var squareTime = 0 // 설정 주입시간(10~300분)
    var squareInjTime = 0 // 경과 주입시간(10~300분)
    var squareAmount = 0.0 // 주입 설정량
    var squareInjAmount = 0.0 // 현재 주입량

    // 12. daul bolus status
    var dualStatus = 0 // 주입상태
    var dualAmount = 0.0 // 일반주입 설정량
    var dualInjAmount = 0.0 // 일반주입량
    var dualSquareTime = 0 // 스퀘어주입 설정시간(10~300분)
    var dualInjSquareTime = 0 // 스퀘어주입 경과시간(10~300분)
    var dualSquareAmount = 0.0 // 스퀘어주입 설정량
    var dualInjSquareAmount = 0.0 // 스퀘어주입량

    // 13. last injection  status
    var recentKind1 = 0 // 최근-1 주입 종류(1=식사, 2=일반간식, 3=스퀘어회식, 4=더블회식)
    var recentTime1 = 0 // 최근-1 주입 시간
    var recentAmount1 = 0.0 // 최근-1 주입량
    var recentKind2 = 0 // 최근-2 주입 종류(1=식사, 2=일반간식, 3=스퀘어회식, 4=더블회식)
    var recentTime2 = 0 // 최근-2 주입 시간
    var recentAmount2 = 0.0 // 최근-2 주입량

    // 14. daily injection status
    var todayBaseAmount = 0.0 // 기저주입 총량
    var todayMealAmount = 0.0 // 식사주입 총량
    var todaySnackAmount = 0.0 // 회식주입 총량

    // 15. meat setting status
    var morningHour = 0 // 아침 개시 시간(0~23)
    var morningAmount = 0.0 // 아침 식전량
    var lunchHour = 0 // 점심 개시 시간(0~23)
    var lunchAmount = 0.0 // 점심 식전량
    var dinnerHour = 0 // 저녁 개시 시간(0~23)
    var dinnerAmount = 0.0 // 저녁 식전량

    // 16. basal injection status at this hour
    var currentBasePattern = 0 // 패턴 종류 (1=기본, 2=생활1, 3=생활2, 4=생활3, 5=닥터1, 6=닥터2)
    var currentBaseHour = 0 // 현재주입시간(0~23)
    var currentBaseTbBeforeAmount = 0.0 // 해당시간의 임시기저 계산 전 기저주입량: 기저주입막힘 발생 시 기저주입 막힘량 제외, 기저정지로 인해 주입되지 않은 량 제외, 리셋으로 인해 주입되지 않은 량 제외(47.5=4750)
    var currentBaseTbAfterAmount = 0.0 // 해당시간의 임시기저 계산 후 기저주입량: 기저주입막힘 발생 시 기저주입 막힘량 제외, 기저정지로 인해 주입되지 않은 량 제외, 리셋으로 인해 주입되지 않은 량 제외(47.5=4750)

    // 17. saved basal pattern status
    var baseAmount1 = 0.00// 주입량 1(량*100, 23.25->2325, 15.2->1520)
    var baseAmount2 = 0.0// 주입량 2(량*100, 23.25->2325, 15.2->1520)
    var baseAmount3 = 0.0 // 주입량 3(량*100, 23.25->2325, 15.2->1520)
    var baseAmount4 = 0.0 // 주입량 4(량*100, 23.25->2325, 15.2->1520)
    var baseAmount5 = 0.0 // 주입량 5(량*100, 23.25->2325, 15.2->1520)
    var baseAmount6 = 0.0 // 주입량 6(량*100, 23.25->2325, 15.2->1520)
    var baseAmount7 = 0.0 // 주입량 7(량*100, 23.25->2325, 15.2->1520)
    var baseAmount8 = 0.0 // 주입량 8(량*100, 23.25->2325, 15.2->1520)
    var baseAmount9 = 0.0 // 주입량 9(량*100, 23.25->2325, 15.2->1520)
    var baseAmount10 = 0.0 // 주입량 10(량*100, 23.25->2325, 15.2->1520)
    var baseAmount11 = 0.0 // 주입량 11(량*100, 23.25->2325, 15.2->1520)
    var baseAmount12 = 0.0 // 주입량 12(량*100, 23.25->2325, 15.2->1520)
    var baseAmount13 = 0.0 // 주입량 13(량*100, 23.25->2325, 15.2->1520)
    var baseAmount14 = 0.0 // 주입량 14(량*100, 23.25->2325, 15.2->1520)
    var baseAmount15 = 0.0 // 주입량 15(량*100, 23.25->2325, 15.2->1520)
    var baseAmount16 = 0.0 // 주입량 16(량*100, 23.25->2325, 15.2->1520)
    var baseAmount17 = 0.0 // 주입량 17(량*100, 23.25->2325, 15.2->1520)
    var baseAmount18 = 0.0 // 주입량 18(량*100, 23.25->2325, 15.2->1520)
    var baseAmount19 = 0.0 // 주입량 19(량*100, 23.25->2325, 15.2->1520)
    var baseAmount20 = 0.0 // 주입량 20(량*100, 23.25->2325, 15.2->1520)
    var baseAmount21 = 0.0 // 주입량 21(량*100, 23.25->2325, 15.2->1520)
    var baseAmount22 = 0.0 // 주입량 22(량*100, 23.25->2325, 15.2->1520)
    var baseAmount23 = 0.0 // 주입량 23(량*100, 23.25->2325, 15.2->1520)
    var baseAmount24 = 0.0 // 주입량 24(량*100, 23.25->2325, 15.2->1520)

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
