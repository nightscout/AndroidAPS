package app.aaps.pump.aaruy.comm

import java.math.BigDecimal
import java.math.RoundingMode

/*
 * What the driver works with: the packets of AaruyPackets.kt in real units.
 * The field names are stored by the driver as JSON to compare profiles, so they must stay.
 */

data class BasalItem(var startTime: Int, var basalSpeed: Double)

data class BasalItems(var injectPerDay: Double, var totalArea: Int, var basalItemList: List<BasalItem>)

data class BasalInfo(var basalItems: BasalItems?)

data class BasalStatus(var index: Int, var curValue: Double, var remainTime: Long, var runningTime: Long)

data class BolusStatus(var state: Int, var curBolusNow: Double, var curBolusDelay: Double, var remainTime: Long, var bolusType: Int)

data class PauseStatus(var index: Int, var pauseOrStopTime: Long)

data class StatusInfo(
    var remainAmount: Int,
    var runningStatus: Int,
    var basalStatus: BasalStatus?,
    var tempBasalStatus: BasalStatus?,
    var bolusStatus: BolusStatus?,
    var pauseStatus: PauseStatus?
)

data class BasicParam(
    var maxBasal: Double,
    var maxBolus: Double,
    var bolusStep: Int,
    var lowAlert: Double,
    var closeInjectSound: Int,
    var pumpType: Int
)

data class DailyItem(var timestamp: Long, var dailyTotal: Double, var dailyBolus: Double)

data class DailyInfo(var count: Int, var dailyItemList: List<DailyItem>)

data class LogItem(var timestamp: Long, var type: Int, var data: Long, var injectTime: Int, var delayBolus: Long, var reserved: Int)

data class LogInfo(var count: Int, var totalPage: Int, var curPage: Int, var curNum: Int, var logItems: List<LogItem>)

private fun divide(value: Int, divisor: String, scale: Int): Double =
    BigDecimal(value.toDouble().toString()).divide(BigDecimal(divisor), scale, RoundingMode.HALF_UP).toDouble()

private fun fromThousandths(value: Short): Double = divide(value.unsigned(), "1000.0", 3)
private fun fromTenths(value: Short): Double = divide(value.unsigned(), "10", 1)

internal fun BasalAreaPacket.toBasalItem() = BasalItem(startTime.unsigned(), fromThousandths(inject))

internal fun BasalInfoPacket.toBasalItems() =
    BasalItems(fromTenths(injectPerDay), totalArea.toInt(), areas.orEmpty().map { it.toBasalItem() })

internal fun BasalSettingPacket.toBasalInfo() = BasalInfo(basalInfo?.toBasalItems())

internal fun BasicParamPacket.toBasicParam() =
    BasicParam(
        maxBasal = fromThousandths(maxBasal),
        maxBolus = fromThousandths(maxBolus),
        bolusStep = bolusStep.toInt(),
        lowAlert = lowAlert.unsigned().toDouble(),
        closeInjectSound = closeInjectSound.toInt(),
        pumpType = pumpType.toInt()
    )

private fun CurrentStatusPacket.BasalValue.toBasalStatus() =
    BasalStatus(index.toInt(), fromThousandths(curValue), remainTime.unsigned().toLong(), runningTime.unsigned().toLong())

private fun CurrentStatusPacket.BolusValue.toBolusStatus() =
    BolusStatus(state.toInt(), curBolusNow.unsigned().toDouble(), curBolusDelay.unsigned().toDouble(), remainTime.unsigned().toLong(), bolusType.toInt())

private fun CurrentStatusPacket.PauseValue.toPauseStatus() = PauseStatus(index.toInt(), pauseOrStopTime.unsigned())

internal fun CurrentStatusPacket.toStatusInfo() =
    StatusInfo(
        remainAmount = remainAmount.unsigned(),
        runningStatus = runningStatus.toInt(),
        basalStatus = basal?.toBasalStatus(),
        tempBasalStatus = tempBasal?.toBasalStatus(),
        bolusStatus = bolus?.toBolusStatus(),
        pauseStatus = pause?.toPauseStatus()
    )

private fun HistoryDailyUnitPacket.toDailyItem() =
    DailyItem(
        timestamp = date.unsigned().toLong() * AaruyPumpTime.DAY_SECONDS * 1000 + AaruyPumpTime.BASE_TIME_MS,
        dailyTotal = fromTenths(dailyTotal),
        dailyBolus = fromTenths(dailyBolus)
    )

internal fun HistoryDailyPacket.toDailyInfo() = DailyInfo(count.unsigned(), units.map { it.toDailyItem() })

private fun HistoryUnitPacket.toLogItem() =
    LogItem(
        timestamp = time.unsigned() * 1000 + AaruyPumpTime.BASE_TIME_MS,
        type = type.toInt(),
        data = data.unsigned().toLong(),
        injectTime = injectTime.unsigned(),
        delayBolus = delayBolus.unsigned().toLong(),
        reserved = reserved.toInt()
    )

internal fun HistoryLogPacket.toLogInfo() =
    LogInfo(count.unsigned(), totalPage.unsigned(), curPage.unsigned(), curNum.unsigned(), units.map { it.toLogItem() })
