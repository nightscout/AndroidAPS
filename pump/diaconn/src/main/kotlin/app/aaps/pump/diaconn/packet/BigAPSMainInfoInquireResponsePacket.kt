package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.pump.diaconn.pumplog.PumpLogUtil
import dagger.android.HasAndroidInjector
import org.joda.time.DateTime
import javax.inject.Inject
import kotlin.math.floor

/**
 * BigAPSMainInfoInquireResponsePacket
 */
@Suppress("SpellCheckingInspection")
class BigAPSMainInfoInquireResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper

    init {
        msgType = 0x94.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BigAPSMainInfoInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BigAPSMainInfoInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result2 = getByteToInt(bufferData)  // 결과비트 상위 4비트 제거
        if (!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }
        // 1. pump system setting info
        diaconnG8Pump.systemRemainInsulin = floor(getShortToInt(bufferData) / 100.0)  // 인슐린 잔량
        diaconnG8Pump.systemRemainBattery = getByteToInt(bufferData) // 베터리잔량(1~100%)
        diaconnG8Pump.systemBasePattern = getByteToInt(bufferData) // 기저주입 패턴(0=없음, 1=기본, 2=생활1, 3=생활2, 4=생활3, 5=닥터1, 6=닥터2)
        diaconnG8Pump.systemTbStatus = getByteToInt(bufferData)    // 임시기저 상태(1=임시기저 중, 2=임시기저 해제)
        diaconnG8Pump.systemInjectionMealStatus = getByteToInt(bufferData) // 식사주입 상태(1=주입중, 2=주입상태아님)
        diaconnG8Pump.systemInjectionSnackStatus = getByteToInt(bufferData) // 일반간식 주입 상태(1=주입중, 2=주입상태아님)
        diaconnG8Pump.systemInjectionSquareStatue = getByteToInt(bufferData) // 스퀘어회식 주입 상태(1=주입중, 2=주입상태아님)
        diaconnG8Pump.systemInjectionDualStatus = getByteToInt(bufferData) // 더블회식 주입 상태(1=주입중, 2=주입상태아님)

        // 2. basal injection suspend status (1:stop, 2:release)
        diaconnG8Pump.basePauseStatus = getByteToInt(bufferData) // 상태(1=정지,2=해제)

        // 3. Pump time
        diaconnG8Pump.year = getByteToInt(bufferData) + 2000   // 년 (18~99)
        diaconnG8Pump.month = getByteToInt(bufferData)  // 월 (1~12)
        diaconnG8Pump.day = getByteToInt(bufferData)    // 일 (1~31)
        diaconnG8Pump.hour = getByteToInt(bufferData)   // 시 (0~23)
        diaconnG8Pump.minute = getByteToInt(bufferData) // 분 (0~59)
        diaconnG8Pump.second = getByteToInt(bufferData) // 초 (0~59)

        //4. pump system info
        diaconnG8Pump.country = getByteToInt(bufferData).toChar().toString().toInt() // ASCII
        diaconnG8Pump.productType = getByteToInt(bufferData).toChar().toString().toInt() // ASCII
        diaconnG8Pump.makeYear = getByteToInt(bufferData)
        diaconnG8Pump.makeMonth = getByteToInt(bufferData)
        diaconnG8Pump.makeDay = getByteToInt(bufferData)
        diaconnG8Pump.lotNo = getByteToInt(bufferData)// LOT NO
        diaconnG8Pump.serialNo = getShortToInt(bufferData)
        diaconnG8Pump.majorVersion = getByteToInt(bufferData)
        diaconnG8Pump.minorVersion = getByteToInt(bufferData)

        // save current pump firmware version
        preferences.put(DiaconnStringNonKey.PumpVersion, diaconnG8Pump.majorVersion.toString() + "." + diaconnG8Pump.minorVersion.toString())

        // 5. pump log status
        diaconnG8Pump.pumpLastLogNum = getShortToInt(bufferData) // last saved log no
        diaconnG8Pump.pumpWrappingCount = getByteToInt(bufferData) // wrapping count

        // 6. bolus speed status.
        diaconnG8Pump.speed = getByteToInt(bufferData) // bolus speed (1~8)

        // 7. Tempbasal status
        diaconnG8Pump.tbStatus = getByteToInt(bufferData) // tempbasal status
        diaconnG8Pump.tbTime = getByteToInt(bufferData)    // tempbasal durationn
        diaconnG8Pump.tbInjectRateRatio = getShortToInt(bufferData) //rate/percent
        diaconnG8Pump.tbElapsedTime = getShortToInt(bufferData) // tempbasal elapsed time(0~1425 min)

        // 8. Basal status
        diaconnG8Pump.baseStatus = getByteToInt(bufferData) // 기저주입 상태
        diaconnG8Pump.baseHour = getByteToInt(bufferData) // 현재 주입시간
        diaconnG8Pump.baseAmount = getShortToInt(bufferData) / 100.0 // 주입설정량
        diaconnG8Pump.baseInjAmount = getShortToInt(bufferData) / 100.0 // 현재 주입량

        // 9. snack bolus status
        diaconnG8Pump.snackStatus = getByteToInt(bufferData) //주입상태
        diaconnG8Pump.snackAmount = getShortToInt(bufferData) / 100.0 // 주입설정량
        diaconnG8Pump.snackInjAmount = getShortToInt(bufferData) / 100.0 // 현재주입량
        diaconnG8Pump.snackSpeed = getByteToInt(bufferData) //주입속도

        // 10. square(extended) bolus status
        diaconnG8Pump.squareStatus = getByteToInt(bufferData) // 주입상태
        diaconnG8Pump.squareTime = getShortToInt(bufferData) // 설정 주입시간(10~300분)
        diaconnG8Pump.squareInjTime = getShortToInt(bufferData) // 경과 주입시간(10~300분)
        diaconnG8Pump.squareAmount = getShortToInt(bufferData) / 100.0  // 주입 설정량
        diaconnG8Pump.squareInjAmount = getShortToInt(bufferData) / 100.0 // 현재 주입량

        // 11. daul bolus status
        diaconnG8Pump.dualStatus = getByteToInt(bufferData) // 주입상태
        diaconnG8Pump.dualAmount = getShortToInt(bufferData) / 100.0 // 일반주입 설정량
        diaconnG8Pump.dualInjAmount = getShortToInt(bufferData) / 100.0 // 일반주입량
        diaconnG8Pump.dualSquareTime = getShortToInt(bufferData) // 스퀘어주입 설정시간(10~300분)
        diaconnG8Pump.dualInjSquareTime = getShortToInt(bufferData) // 스퀘어주입 경과시간(10~300분)
        diaconnG8Pump.dualSquareAmount = getShortToInt(bufferData) / 100.0 // 스퀘어주입 설정량
        diaconnG8Pump.dualInjSquareAmount = getShortToInt(bufferData) / 100.0 // 스퀘어주입량

        // 12. last injection  status
        diaconnG8Pump.recentKind1 = getByteToInt(bufferData) // 최근-1 주입 종류(1=식사, 2=일반간식, 3=스퀘어회식, 4=더블회식)
        diaconnG8Pump.recentTime1 = getIntToInt(bufferData) // 최근-1 주입 시간
        diaconnG8Pump.recentAmount1 = getShortToInt(bufferData) / 100.0 // 최근-1 주입량
        diaconnG8Pump.recentKind2 = getByteToInt(bufferData) // 최근-2 주입 종류(1=식사, 2=일반간식, 3=스퀘어회식, 4=더블회식)
        diaconnG8Pump.recentTime2 = getIntToInt(bufferData) // 최근-2 주입 시간
        diaconnG8Pump.recentAmount2 = getShortToInt(bufferData) / 100.0 // 최근-2 주입량

        // 13. daily injection status
        diaconnG8Pump.todayBaseAmount = getShortToInt(bufferData) / 100.0 // 기저주입 총량
        diaconnG8Pump.todayMealAmount = getShortToInt(bufferData) / 100.0 // 식사주입 총량
        diaconnG8Pump.todaySnackAmount = getShortToInt(bufferData) / 100.0  // 회식주입 총량

        // 14. basal injection status at this hour
        diaconnG8Pump.currentBasePattern = getByteToInt(bufferData) // 패턴 종류 (1=기본, 2=생활1, 3=생활2, 4=생활3, 5=닥터1, 6=닥터2)
        diaconnG8Pump.currentBaseHour = getByteToInt(bufferData) // 현재주입시간(0~23)
        diaconnG8Pump.currentBaseTbBeforeAmount = getShortToInt(bufferData) / 100.0 // 해당시간의 임시기저 계산 전 기저주입량: 기저주입막힘 발생 시 기저주입 막힘량 제외, 기저정지로 인해 주입되지 않은 량 제외, 리셋으로 인해 주입되지 않은 량 제외(47.5=4750)
        diaconnG8Pump.currentBaseTbAfterAmount = getShortToInt(bufferData) / 100.0 // 해당시간의 임시기저 계산 후 기저주입량: 기저주입막힘 발생 시 기저주입 막힘량 제외, 기저정지로 인해 주입되지 않은 량 제외, 리셋으로 인해 주입되지 않은 량 제외(47.5=4750)

        // 15. saved basal pattern status
        diaconnG8Pump.baseAmount1 = getShortToInt(bufferData) / 100.0  // 주입량 1(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount2 = getShortToInt(bufferData) / 100.0  // 주입량 2(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount3 = getShortToInt(bufferData) / 100.0  // 주입량 3(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount4 = getShortToInt(bufferData) / 100.0  // 주입량 4(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount5 = getShortToInt(bufferData) / 100.0  // 주입량 5(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount6 = getShortToInt(bufferData) / 100.0  // 주입량 6(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount7 = getShortToInt(bufferData) / 100.0  // 주입량 7(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount8 = getShortToInt(bufferData) / 100.0  // 주입량 8(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount9 = getShortToInt(bufferData) / 100.0  // 주입량 9(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount10 = getShortToInt(bufferData) / 100.0  // 주입량 10(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount11 = getShortToInt(bufferData) / 100.0  // 주입량 11(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount12 = getShortToInt(bufferData) / 100.0  // 주입량 12(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount13 = getShortToInt(bufferData) / 100.0  // 주입량 13(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount14 = getShortToInt(bufferData) / 100.0  // 주입량 14(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount15 = getShortToInt(bufferData) / 100.0  // 주입량 15(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount16 = getShortToInt(bufferData) / 100.0  // 주입량 16(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount17 = getShortToInt(bufferData) / 100.0  // 주입량 17(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount18 = getShortToInt(bufferData) / 100.0  // 주입량 18(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount19 = getShortToInt(bufferData) / 100.0  // 주입량 19(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount20 = getShortToInt(bufferData) / 100.0  // 주입량 20(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount21 = getShortToInt(bufferData) / 100.0  // 주입량 21(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount22 = getShortToInt(bufferData) / 100.0  // 주입량 22(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount23 = getShortToInt(bufferData) / 100.0  // 주입량 23(량*100, 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount24 = getShortToInt(bufferData) / 100.0  // 주입량 24(량*100, 23.25->2325, 15.2->1520)

        // 16. 1hour basal limit
        diaconnG8Pump.maxBasalPerHours = getShortToInt(bufferData).toDouble() / 100.0  // not include tempbasal limit
        diaconnG8Pump.maxBasal = diaconnG8Pump.maxBasalPerHours * 2.5 // include tempbasal

        // 17. snack limit
        diaconnG8Pump.mealLimitTime = getByteToInt(bufferData) // mealLimittime
        diaconnG8Pump.maxBolus = getShortToInt(bufferData).toDouble() / 100
        diaconnG8Pump.maxBolusePerDay = getShortToInt(bufferData).toDouble() / 100

        // 18. pump sound
        diaconnG8Pump.beepAndAlarm = getByteToInt(bufferData) - 1
        diaconnG8Pump.alarmIntensity = getByteToInt(bufferData) - 1

        // 19. pump light time
        diaconnG8Pump.lcdOnTimeSec = getByteToInt(bufferData) // kind (1=30 sec, 2=40 sec, 3=50 sec)

        // 20. language
        diaconnG8Pump.selectedLanguage = getByteToInt(bufferData) // language (1=Chiness, 2=Korean, 3=English)

        // pump time setting 'yyyy-MM-dd'T'HH:mm:ssZ'	“2019-07-04T12:30:30+0530”
        val time = DateTime(diaconnG8Pump.year, diaconnG8Pump.month, diaconnG8Pump.day, diaconnG8Pump.hour, diaconnG8Pump.minute, diaconnG8Pump.second)
        diaconnG8Pump.setPumpTime(time.millis)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time " + dateUtil.dateAndTimeAndSecondsString(time.millis))

        // basal pattern from pump
        diaconnG8Pump.pumpProfiles = Array(4) { Array(24) { 0.0 } }
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][0] = diaconnG8Pump.baseAmount1
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][1] = diaconnG8Pump.baseAmount2
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][2] = diaconnG8Pump.baseAmount3
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][3] = diaconnG8Pump.baseAmount4
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][4] = diaconnG8Pump.baseAmount5
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][5] = diaconnG8Pump.baseAmount6
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][6] = diaconnG8Pump.baseAmount7
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][7] = diaconnG8Pump.baseAmount8
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][8] = diaconnG8Pump.baseAmount9
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][9] = diaconnG8Pump.baseAmount10
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][10] = diaconnG8Pump.baseAmount11
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][11] = diaconnG8Pump.baseAmount12
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][12] = diaconnG8Pump.baseAmount13
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][13] = diaconnG8Pump.baseAmount14
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][14] = diaconnG8Pump.baseAmount15
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][15] = diaconnG8Pump.baseAmount16
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][16] = diaconnG8Pump.baseAmount17
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][17] = diaconnG8Pump.baseAmount18
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][18] = diaconnG8Pump.baseAmount19
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][19] = diaconnG8Pump.baseAmount20
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][20] = diaconnG8Pump.baseAmount21
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][21] = diaconnG8Pump.baseAmount22
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][22] = diaconnG8Pump.baseAmount23
        diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][23] = diaconnG8Pump.baseAmount24

        //incarnation no 처리
        diaconnG8Pump.isPumpVersionGe2_63 = PumpLogUtil.isPumpVersionGe(preferences.get(DiaconnStringNonKey.PumpVersion), 2, 63)
        diaconnG8Pump.isPumpVersionGe3_53 = PumpLogUtil.isPumpVersionGe(preferences.get(DiaconnStringNonKey.PumpVersion), 3, 53)

        aapsLogger.debug(LTag.PUMPCOMM, "result > " + diaconnG8Pump.result)
        aapsLogger.debug(LTag.PUMPCOMM, "systemRemainInsulin > " + diaconnG8Pump.systemRemainInsulin)
        aapsLogger.debug(LTag.PUMPCOMM, "systemRemainBattery > " + diaconnG8Pump.systemRemainBattery)
        aapsLogger.debug(LTag.PUMPCOMM, "systemBasePattern > " + diaconnG8Pump.systemBasePattern)
        aapsLogger.debug(LTag.PUMPCOMM, "systemTbStatus > " + diaconnG8Pump.systemTbStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "systemInjectionMealStatus > " + diaconnG8Pump.systemInjectionMealStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "systemInjectionSnackStatus > " + diaconnG8Pump.systemInjectionSnackStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "systemInjectionSquareStatue > " + diaconnG8Pump.systemInjectionSquareStatue)
        aapsLogger.debug(LTag.PUMPCOMM, "systemInjectionDualStatus > " + diaconnG8Pump.systemInjectionDualStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "basePauseStatus > " + diaconnG8Pump.basePauseStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "year > " + diaconnG8Pump.year)
        aapsLogger.debug(LTag.PUMPCOMM, "month > " + diaconnG8Pump.month)
        aapsLogger.debug(LTag.PUMPCOMM, "day > " + diaconnG8Pump.day)
        aapsLogger.debug(LTag.PUMPCOMM, "hour > " + diaconnG8Pump.hour)
        aapsLogger.debug(LTag.PUMPCOMM, "minute > " + diaconnG8Pump.minute)
        aapsLogger.debug(LTag.PUMPCOMM, "second > " + diaconnG8Pump.second)
        aapsLogger.debug(LTag.PUMPCOMM, "country > " + diaconnG8Pump.country)
        aapsLogger.debug(LTag.PUMPCOMM, "productType > " + diaconnG8Pump.productType)
        aapsLogger.debug(LTag.PUMPCOMM, "makeYear > " + diaconnG8Pump.makeYear)
        aapsLogger.debug(LTag.PUMPCOMM, "makeMonth > " + diaconnG8Pump.makeMonth)
        aapsLogger.debug(LTag.PUMPCOMM, "makeDay > " + diaconnG8Pump.makeDay)
        aapsLogger.debug(LTag.PUMPCOMM, "lotNo > " + diaconnG8Pump.lotNo)
        aapsLogger.debug(LTag.PUMPCOMM, "serialNo > " + diaconnG8Pump.serialNo)
        aapsLogger.debug(LTag.PUMPCOMM, "majorVersion > " + diaconnG8Pump.majorVersion)
        aapsLogger.debug(LTag.PUMPCOMM, "minorVersion > " + diaconnG8Pump.minorVersion)
        aapsLogger.debug(LTag.PUMPCOMM, "lastNum  > " + diaconnG8Pump.pumpLastLogNum)
        aapsLogger.debug(LTag.PUMPCOMM, "wrappingCount > " + diaconnG8Pump.pumpWrappingCount)
        aapsLogger.debug(LTag.PUMPCOMM, "speed > " + diaconnG8Pump.speed)
        aapsLogger.debug(LTag.PUMPCOMM, "tbStatus > " + diaconnG8Pump.tbStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "tbTime> " + diaconnG8Pump.tbTime)
        aapsLogger.debug(LTag.PUMPCOMM, "tbInjectRateRatio > " + diaconnG8Pump.tbInjectRateRatio)
        aapsLogger.debug(LTag.PUMPCOMM, "tbElapsedTime > " + diaconnG8Pump.tbElapsedTime)
        aapsLogger.debug(LTag.PUMPCOMM, "baseStatus > " + diaconnG8Pump.baseStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "baseHour > " + diaconnG8Pump.baseHour)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount > " + diaconnG8Pump.baseAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "baseInjAmount > " + diaconnG8Pump.baseInjAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "mealKind > " + diaconnG8Pump.mealKind)
        aapsLogger.debug(LTag.PUMPCOMM, "mealStartTime > " + diaconnG8Pump.mealStartTime)
        aapsLogger.debug(LTag.PUMPCOMM, "mealStatus > " + diaconnG8Pump.mealStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "mealAmount > " + diaconnG8Pump.mealAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "mealInjAmount > " + diaconnG8Pump.mealInjAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "mealSpeed > " + diaconnG8Pump.mealSpeed)
        aapsLogger.debug(LTag.PUMPCOMM, "snackStatus > " + diaconnG8Pump.snackStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "snackAmount > " + diaconnG8Pump.snackAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "snackInjAmount > " + diaconnG8Pump.snackInjAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "snackSpeed > " + diaconnG8Pump.snackSpeed)
        aapsLogger.debug(LTag.PUMPCOMM, "squareStatus > " + diaconnG8Pump.squareStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "squareTime > " + diaconnG8Pump.squareTime)
        aapsLogger.debug(LTag.PUMPCOMM, "squareInjTime > " + diaconnG8Pump.squareInjTime)
        aapsLogger.debug(LTag.PUMPCOMM, "squareAmount > " + diaconnG8Pump.squareAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "squareInjAmount > " + diaconnG8Pump.squareInjAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "dualStatus > " + diaconnG8Pump.dualStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "dualAmount > " + diaconnG8Pump.dualAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "dualInjAmount > " + diaconnG8Pump.dualInjAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "dualSquareTime > " + diaconnG8Pump.dualSquareTime)
        aapsLogger.debug(LTag.PUMPCOMM, "dualInjSquareTime > " + diaconnG8Pump.dualInjSquareTime)
        aapsLogger.debug(LTag.PUMPCOMM, "dualSquareAmount > " + diaconnG8Pump.dualSquareAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "dualInjSquareAmount> " + diaconnG8Pump.dualInjSquareAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "recentKind1  > " + diaconnG8Pump.recentKind1)
        aapsLogger.debug(LTag.PUMPCOMM, "recentTime1  > " + diaconnG8Pump.recentTime1)
        aapsLogger.debug(LTag.PUMPCOMM, "recentAmount1 > " + diaconnG8Pump.recentAmount1)
        aapsLogger.debug(LTag.PUMPCOMM, "recentKind2  	> " + diaconnG8Pump.recentKind2)
        aapsLogger.debug(LTag.PUMPCOMM, "recentTime2  	> " + diaconnG8Pump.recentTime2)
        aapsLogger.debug(LTag.PUMPCOMM, "recentAmount2 > " + diaconnG8Pump.recentAmount2)
        aapsLogger.debug(LTag.PUMPCOMM, "todayBaseAmount > " + diaconnG8Pump.todayBaseAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "todayMealAmount > " + diaconnG8Pump.todayMealAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "todaySnackAmount > " + diaconnG8Pump.todaySnackAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "morningHour  > " + diaconnG8Pump.morningHour)
        aapsLogger.debug(LTag.PUMPCOMM, "morningAmount > " + diaconnG8Pump.morningAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "lunchHour  > " + diaconnG8Pump.lunchHour)
        aapsLogger.debug(LTag.PUMPCOMM, "lunchAmount > " + diaconnG8Pump.lunchAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "dinnerHour  > " + diaconnG8Pump.dinnerHour)
        aapsLogger.debug(LTag.PUMPCOMM, "dinnerAmount > " + diaconnG8Pump.dinnerAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "currentBasePattern > " + diaconnG8Pump.currentBasePattern)
        aapsLogger.debug(LTag.PUMPCOMM, "currentBaseHour  > " + diaconnG8Pump.currentBaseHour)
        aapsLogger.debug(LTag.PUMPCOMM, "currentBaseTbBeforeAmount > " + diaconnG8Pump.currentBaseTbBeforeAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "currentBaseTbAfterAmount > " + diaconnG8Pump.currentBaseTbAfterAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount1  > " + diaconnG8Pump.baseAmount1)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount2  > " + diaconnG8Pump.baseAmount2)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount3  > " + diaconnG8Pump.baseAmount3)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount4  > " + diaconnG8Pump.baseAmount4)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount5  > " + diaconnG8Pump.baseAmount5)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount6  > " + diaconnG8Pump.baseAmount6)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount7  > " + diaconnG8Pump.baseAmount7)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount8  > " + diaconnG8Pump.baseAmount8)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount9  > " + diaconnG8Pump.baseAmount9)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount10 > " + diaconnG8Pump.baseAmount10)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount11 > " + diaconnG8Pump.baseAmount11)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount12 > " + diaconnG8Pump.baseAmount12)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount13 > " + diaconnG8Pump.baseAmount13)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount14 > " + diaconnG8Pump.baseAmount14)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount15 > " + diaconnG8Pump.baseAmount15)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount16 > " + diaconnG8Pump.baseAmount16)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount17 > " + diaconnG8Pump.baseAmount17)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount18 > " + diaconnG8Pump.baseAmount18)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount19 > " + diaconnG8Pump.baseAmount19)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount20 > " + diaconnG8Pump.baseAmount20)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount21 > " + diaconnG8Pump.baseAmount21)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount22 > " + diaconnG8Pump.baseAmount22)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount23 > " + diaconnG8Pump.baseAmount23)
        aapsLogger.debug(LTag.PUMPCOMM, "baseAmount24 > " + diaconnG8Pump.baseAmount24)
        aapsLogger.debug(LTag.PUMPCOMM, "maxBasalPerHours > " + diaconnG8Pump.maxBasalPerHours)
        aapsLogger.debug(LTag.PUMPCOMM, "maxBasal > " + diaconnG8Pump.maxBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "maxBolus > " + diaconnG8Pump.maxBolus)
        aapsLogger.debug(LTag.PUMPCOMM, "maxBolusePerDay > " + diaconnG8Pump.maxBolusePerDay)
        aapsLogger.debug(LTag.PUMPCOMM, "mealLimitTime > " + diaconnG8Pump.mealLimitTime)
        aapsLogger.debug(LTag.PUMPCOMM, "beepAndAlarm > " + diaconnG8Pump.beepAndAlarm)
        aapsLogger.debug(LTag.PUMPCOMM, "alarmIntesity > " + diaconnG8Pump.alarmIntensity)
        aapsLogger.debug(LTag.PUMPCOMM, "lcdOnTimeSec > " + diaconnG8Pump.lcdOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "selectedLanguage > " + diaconnG8Pump.selectedLanguage)
    }

    override val friendlyName = "PUMP_BIG_APS_MAIN_INFO_INQUIRE_RESPONSE"
}