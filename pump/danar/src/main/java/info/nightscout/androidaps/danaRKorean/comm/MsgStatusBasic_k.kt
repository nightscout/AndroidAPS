package info.nightscout.androidaps.danaRKorean.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.MessageBase

class MsgStatusBasic_k(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x020A)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val currentBasal = intFromBuff(bytes, 0, 2) / 100.0
        val batteryRemaining = intFromBuff(bytes, 2, 1)
        val reservoirRemainingUnits = intFromBuff(bytes, 3, 3) / 750.0
        val dailyTotalUnits = intFromBuff(bytes, 6, 3) / 750.0
        val maxDailyTotalUnits = intFromBuff(bytes, 9, 2) / 100
        danaPump.dailyTotalUnits = dailyTotalUnits
        danaPump.maxDailyTotalUnits = maxDailyTotalUnits
        danaPump.reservoirRemainingUnits = reservoirRemainingUnits
        danaPump.currentBasal = currentBasal
        danaPump.batteryRemaining = batteryRemaining
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: $dailyTotalUnits")
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily total units: $maxDailyTotalUnits")
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: $reservoirRemainingUnits")
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: $currentBasal")
    }
}