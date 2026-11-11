package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.pump.diaconn.pumplog.PumpLogUtil
import dev.zacsweers.metro.Inject
import kotlin.math.floor

/**
 * Answer to [BigAPSMainInfoInquirePacket]: the pump status in one big packet, with the basal and bolus limits and the
 * sound, display and language settings too. The values are stored in [DiaconnG8Pump].
 */
@Suppress("SpellCheckingInspection")
class BigAPSMainInfoInquireResponsePacket(
    injector: MetroMemberInjector
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
        val result2 = getByteToInt(bufferData)  // result byte: 16 is a good answer to an inquiry
        if (!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }
        // 1. pump system setting info
        diaconnG8Pump.systemRemainInsulin = floor(getShortToInt(bufferData) / 100.0)  // insulin left in the reservoir
        diaconnG8Pump.systemRemainBattery = getByteToInt(bufferData) // battery left (1~100%)
        diaconnG8Pump.systemBasePattern = getByteToInt(bufferData) // basal pattern (0=none, 1=basic, 2=life 1, 3=life 2, 4=life 3, 5=doctor 1, 6=doctor 2)
        diaconnG8Pump.systemTbStatus = getByteToInt(bufferData)    // temp basal state (1=running, 2=released)
        diaconnG8Pump.systemInjectionMealStatus = getByteToInt(bufferData) // meal bolus state (1=delivering, 2=not delivering)
        diaconnG8Pump.systemInjectionSnackStatus = getByteToInt(bufferData) // snack bolus state (1=delivering, 2=not delivering)
        diaconnG8Pump.systemInjectionSquareStatue = getByteToInt(bufferData) // square (extended) bolus state (1=delivering, 2=not delivering)
        diaconnG8Pump.systemInjectionDualStatus = getByteToInt(bufferData) // dual bolus state (1=delivering, 2=not delivering)

        // 2. basal injection suspend status (1:stop, 2:release)
        diaconnG8Pump.basePauseStatus = getByteToInt(bufferData) // state (1=stopped, 2=released)

        // 3. Pump time: year (18~99), month, day, hour, minute, second
        diaconnG8Pump.readPumpClock(
            year = getByteToInt(bufferData) + 2000,
            month = getByteToInt(bufferData),
            day = getByteToInt(bufferData),
            hour = getByteToInt(bufferData),
            minute = getByteToInt(bufferData),
            second = getByteToInt(bufferData)
        )

        //4. pump system info
        diaconnG8Pump.country = Char(getByteToInt(bufferData)).toString().toInt() // ASCII
        diaconnG8Pump.productType = Char(getByteToInt(bufferData)).toString().toInt() // ASCII
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
        diaconnG8Pump.tbTime = getByteToInt(bufferData)    // tempbasal duration
        diaconnG8Pump.tbInjectRateRatio = getShortToInt(bufferData) //rate/percent
        diaconnG8Pump.tbElapsedTime = getShortToInt(bufferData) // tempbasal elapsed time(0~1425 min)

        // 8. Basal status
        diaconnG8Pump.baseStatus = getByteToInt(bufferData) // basal delivery state
        diaconnG8Pump.baseHour = getByteToInt(bufferData) // hour of the current delivery
        diaconnG8Pump.baseAmount = getShortToInt(bufferData) / 100.0 // set amount
        diaconnG8Pump.baseInjAmount = getShortToInt(bufferData) / 100.0 // amount delivered so far

        // 9. snack bolus status
        diaconnG8Pump.snackStatus = getByteToInt(bufferData) // delivery state
        diaconnG8Pump.snackAmount = getShortToInt(bufferData) / 100.0 // set amount
        diaconnG8Pump.snackInjAmount = getShortToInt(bufferData) / 100.0 // amount delivered so far
        diaconnG8Pump.snackSpeed = getByteToInt(bufferData) // delivery speed

        // 10. square(extended) bolus status
        diaconnG8Pump.squareStatus = getByteToInt(bufferData) // delivery state
        diaconnG8Pump.squareTime = getShortToInt(bufferData) // set duration (10~300 min)
        diaconnG8Pump.squareInjTime = getShortToInt(bufferData) // elapsed time (10~300 min)
        diaconnG8Pump.squareAmount = getShortToInt(bufferData) / 100.0  // set amount
        diaconnG8Pump.squareInjAmount = getShortToInt(bufferData) / 100.0 // amount delivered so far

        // 11. dual bolus status
        diaconnG8Pump.dualStatus = getByteToInt(bufferData) // delivery state
        diaconnG8Pump.dualAmount = getShortToInt(bufferData) / 100.0 // set amount of the normal part
        diaconnG8Pump.dualInjAmount = getShortToInt(bufferData) / 100.0 // amount delivered of the normal part
        diaconnG8Pump.dualSquareTime = getShortToInt(bufferData) // set duration of the square part (10~300 min)
        diaconnG8Pump.dualInjSquareTime = getShortToInt(bufferData) // elapsed time of the square part (10~300 min)
        diaconnG8Pump.dualSquareAmount = getShortToInt(bufferData) / 100.0 // set amount of the square part
        diaconnG8Pump.dualInjSquareAmount = getShortToInt(bufferData) / 100.0 // amount delivered of the square part

        // 12. last injection  status
        diaconnG8Pump.recentKind1 = getByteToInt(bufferData) // kind of the last bolus (1=meal, 2=snack, 3=square, 4=dual)
        diaconnG8Pump.recentTime1 = getIntToInt(bufferData) // time of the last bolus
        diaconnG8Pump.recentAmount1 = getShortToInt(bufferData) / 100.0 // amount of the last bolus
        diaconnG8Pump.recentKind2 = getByteToInt(bufferData) // kind of the bolus before the last one (1=meal, 2=snack, 3=square, 4=dual)
        diaconnG8Pump.recentTime2 = getIntToInt(bufferData) // time of the bolus before the last one
        diaconnG8Pump.recentAmount2 = getShortToInt(bufferData) / 100.0 // amount of the bolus before the last one

        // 13. daily injection status
        diaconnG8Pump.todayBaseAmount = getShortToInt(bufferData) / 100.0 // total basal today
        diaconnG8Pump.todayMealAmount = getShortToInt(bufferData) / 100.0 // total meal bolus today
        diaconnG8Pump.todaySnackAmount = getShortToInt(bufferData) / 100.0  // total of the other boluses today (snack, square, dual)

        // 14. basal injection status at this hour
        diaconnG8Pump.currentBasePattern = getByteToInt(bufferData) // pattern kind (1=basic, 2=life 1, 3=life 2, 4=life 3, 5=doctor 1, 6=doctor 2)
        diaconnG8Pump.currentBaseHour = getByteToInt(bufferData) // hour of the current delivery (0~23)
        diaconnG8Pump.currentBaseTbBeforeAmount = getShortToInt(bufferData) / 100.0 // basal amount of this hour before the temp basal is applied. Not counted: the amount blocked by an occlusion, the amount not delivered while basal was stopped, the amount not delivered because of a reset (47.5=4750)
        diaconnG8Pump.currentBaseTbAfterAmount = getShortToInt(bufferData) / 100.0 // basal amount of this hour after the temp basal is applied. Not counted: the amount blocked by an occlusion, the amount not delivered while basal was stopped, the amount not delivered because of a reset (47.5=4750)

        // 15. saved basal pattern status
        diaconnG8Pump.baseAmount1 = getShortToInt(bufferData) / 100.0  // basal amount 1 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount2 = getShortToInt(bufferData) / 100.0  // basal amount 2 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount3 = getShortToInt(bufferData) / 100.0  // basal amount 3 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount4 = getShortToInt(bufferData) / 100.0  // basal amount 4 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount5 = getShortToInt(bufferData) / 100.0  // basal amount 5 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount6 = getShortToInt(bufferData) / 100.0  // basal amount 6 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount7 = getShortToInt(bufferData) / 100.0  // basal amount 7 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount8 = getShortToInt(bufferData) / 100.0  // basal amount 8 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount9 = getShortToInt(bufferData) / 100.0  // basal amount 9 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount10 = getShortToInt(bufferData) / 100.0  // basal amount 10 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount11 = getShortToInt(bufferData) / 100.0  // basal amount 11 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount12 = getShortToInt(bufferData) / 100.0  // basal amount 12 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount13 = getShortToInt(bufferData) / 100.0  // basal amount 13 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount14 = getShortToInt(bufferData) / 100.0  // basal amount 14 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount15 = getShortToInt(bufferData) / 100.0  // basal amount 15 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount16 = getShortToInt(bufferData) / 100.0  // basal amount 16 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount17 = getShortToInt(bufferData) / 100.0  // basal amount 17 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount18 = getShortToInt(bufferData) / 100.0  // basal amount 18 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount19 = getShortToInt(bufferData) / 100.0  // basal amount 19 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount20 = getShortToInt(bufferData) / 100.0  // basal amount 20 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount21 = getShortToInt(bufferData) / 100.0  // basal amount 21 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount22 = getShortToInt(bufferData) / 100.0  // basal amount 22 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount23 = getShortToInt(bufferData) / 100.0  // basal amount 23 (pump sends amount*100: 23.25->2325, 15.2->1520)
        diaconnG8Pump.baseAmount24 = getShortToInt(bufferData) / 100.0  // basal amount 24 (pump sends amount*100: 23.25->2325, 15.2->1520)

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
        diaconnG8Pump.selectedLanguage = getByteToInt(bufferData) // language (1=Chinese, 2=Korean, 3=English)

        if (!diaconnG8Pump.pumpClockImpossible) aapsLogger.debug(LTag.PUMPCOMM, "Pump time " + dateUtil.dateAndTimeAndSecondsString(diaconnG8Pump.getPumpTime()))

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

        // incarnation number handling: which features this pump version has
        diaconnG8Pump.isPumpVersionGe2_63 = PumpLogUtil.isPumpVersionGe(preferences.get(DiaconnStringNonKey.PumpVersion), 2, 63)
        diaconnG8Pump.isPumpVersionGe3_53 = PumpLogUtil.isPumpVersionGe(preferences.get(DiaconnStringNonKey.PumpVersion), 3, 53)
        diaconnG8Pump.isPumpVersionGe3_58 = PumpLogUtil.isPumpVersionGe(preferences.get(DiaconnStringNonKey.PumpVersion), 3, 58)

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