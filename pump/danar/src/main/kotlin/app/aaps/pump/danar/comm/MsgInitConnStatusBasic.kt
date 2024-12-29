package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import dagger.android.HasAndroidInjector

class MsgInitConnStatusBasic(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0303)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 < 21) {
            return
        }
        danaPump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1
        danaPump.calculatorEnabled = intFromBuff(bytes, 1, 1) == 1
        danaPump.dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750.0
        danaPump.maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100
        danaPump.reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750.0
        danaPump.bolusBlocked = intFromBuff(bytes, 10, 1) == 1
        danaPump.currentBasal = intFromBuff(bytes, 11, 2) / 100.0
        val tempBasalPercent = intFromBuff(bytes, 13, 1)
        val isExtendedInProgress = intFromBuff(bytes, 14, 1) == 1
        //val isTempBasalInProgress = intFromBuff(bytes, 15, 1) == 1
        val statusBasalUDOption = intFromBuff(bytes, 16, 1)
        danaPump.isDualBolusInProgress = intFromBuff(bytes, 17, 1) == 1
        val extendedBolusRate = intFromBuff(bytes, 18, 2) / 100.0
        danaPump.batteryRemaining = intFromBuff(bytes, 20, 1)
        val bolusConfig = intFromBuff(bytes, 21, 1)
        val deliveryPrime = bolusConfig and DanaPump.DELIVERY_PRIME != 0
        val deliveryStepBolus = bolusConfig and DanaPump.DELIVERY_STEP_BOLUS != 0
        val deliveryBasal = bolusConfig and DanaPump.DELIVERY_BASAL != 0
        val deliveryExtBolus = bolusConfig and DanaPump.DELIVERY_EXT_BOLUS != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery prime: $deliveryPrime")
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery step bolus: $deliveryStepBolus")
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery basal: $deliveryBasal")
        aapsLogger.debug(LTag.PUMPCOMM, "Delivery ext bolus: $deliveryExtBolus")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump suspended: " + danaPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "Calculator enabled: " + danaPump.calculatorEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: " + danaPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily total units: " + danaPump.maxDailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: " + danaPump.reservoirRemainingUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus blocked: " + danaPump.bolusBlocked)
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: " + danaPump.currentBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal percent: $tempBasalPercent")
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: $isExtendedInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "statusBasalUDOption: $statusBasalUDOption")
        aapsLogger.debug(LTag.PUMPCOMM, "Is dual bolus running: " + danaPump.isDualBolusInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus rate: $extendedBolusRate")
        aapsLogger.debug(LTag.PUMPCOMM, "Battery remaining: " + danaPump.batteryRemaining)
    }
}