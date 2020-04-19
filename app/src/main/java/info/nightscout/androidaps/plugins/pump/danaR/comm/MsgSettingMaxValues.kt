package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgSettingMaxValues(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x3205)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.maxBolus = intFromBuff(bytes, 0, 2) / 100.0
        danaRPump.maxBasal = intFromBuff(bytes, 2, 2) / 100.0
        danaRPump.maxDailyTotalUnits = intFromBuff(bytes, 4, 2) / 100
        aapsLogger.debug(LTag.PUMPCOMM, "Max bolus: " + danaRPump.maxBolus)
        aapsLogger.debug(LTag.PUMPCOMM, "Max basal: " + danaRPump.maxBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "Total daily max units: " + danaRPump.maxDailyTotalUnits)
    }
}