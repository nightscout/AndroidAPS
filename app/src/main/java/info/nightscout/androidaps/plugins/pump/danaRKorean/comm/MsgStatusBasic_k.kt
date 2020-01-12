package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase

class MsgStatusBasic_k(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x020A)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val currentBasal = intFromBuff(bytes, 0, 2) / 100.0
        val batteryRemaining = intFromBuff(bytes, 2, 1)
        val reservoirRemainingUnits = intFromBuff(bytes, 3, 3) / 750.0
        val dailyTotalUnits = intFromBuff(bytes, 6, 3) / 750.0
        val maxDailyTotalUnits = intFromBuff(bytes, 9, 2) / 100
        danaRPump.dailyTotalUnits = dailyTotalUnits
        danaRPump.maxDailyTotalUnits = maxDailyTotalUnits
        danaRPump.reservoirRemainingUnits = reservoirRemainingUnits
        danaRPump.currentBasal = currentBasal
        danaRPump.batteryRemaining = batteryRemaining
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: $dailyTotalUnits")
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily total units: $maxDailyTotalUnits")
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: $reservoirRemainingUnits")
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: $currentBasal")
    }
}