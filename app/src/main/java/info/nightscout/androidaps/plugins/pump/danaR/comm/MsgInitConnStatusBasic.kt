package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgInitConnStatusBasic(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x0303)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 < 21) {
            return
        }
        danaRPump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1
        danaRPump.calculatorEnabled = intFromBuff(bytes, 1, 1) == 1
        danaRPump.dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750.0
        danaRPump.maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100
        danaRPump.reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750.0
        danaRPump.bolusBlocked = intFromBuff(bytes, 10, 1) == 1
        danaRPump.currentBasal = intFromBuff(bytes, 11, 2) / 100.0
        danaRPump.tempBasalPercent = intFromBuff(bytes, 13, 1)
        danaRPump.isExtendedInProgress = intFromBuff(bytes, 14, 1) == 1
        danaRPump.isTempBasalInProgress = intFromBuff(bytes, 15, 1) == 1
        val statusBasalUDOption = intFromBuff(bytes, 16, 1)
        danaRPump.isDualBolusInProgress = intFromBuff(bytes, 17, 1) == 1
        val extendedBolusRate = intFromBuff(bytes, 18, 2) / 100.0
        danaRPump.batteryRemaining = intFromBuff(bytes, 20, 1)
        val bolusConfig = intFromBuff(bytes, 21, 1)
        val deliveryPrime = bolusConfig and DanaRPump.DELIVERY_PRIME != 0
        val deliveryStepBolus = bolusConfig and DanaRPump.DELIVERY_STEP_BOLUS != 0
        val deliveryBasal = bolusConfig and DanaRPump.DELIVERY_BASAL != 0
        val deliveryExtBolus = bolusConfig and DanaRPump.DELIVERY_EXT_BOLUS != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery prime: $deliveryPrime")
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery step bolus: $deliveryStepBolus")
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery basal: $deliveryBasal")
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery ext bolus: $deliveryExtBolus")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump suspended: " + danaRPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "Calculator enabled: " + danaRPump.calculatorEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: " + danaRPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily total units: " + danaRPump.maxDailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: " + danaRPump.reservoirRemainingUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus blocked: " + danaRPump.bolusBlocked)
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: " + danaRPump.currentBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal percent: " + danaRPump.tempBasalPercent)
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: " + danaRPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "statusBasalUDOption: $statusBasalUDOption")
        aapsLogger.debug(LTag.PUMPCOMM, "Is dual bolus running: " + danaRPump.isDualBolusInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus rate: $extendedBolusRate")
        aapsLogger.debug(LTag.PUMPCOMM, "Battery remaining: " + danaRPump.batteryRemaining)
    }
}