package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump

class MsgSettingMaxValues(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : MessageBase() {

    init {
        SetCommand(0x3205)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.maxBolus = intFromBuff(bytes, 0, 2) / 100.0
        danaPump.maxBasal = intFromBuff(bytes, 2, 2) / 100.0
        danaPump.maxDailyTotalUnits = intFromBuff(bytes, 4, 2) / 100
        aapsLogger.debug(LTag.PUMPCOMM, "Max bolus: " + danaPump.maxBolus)
        aapsLogger.debug(LTag.PUMPCOMM, "Max basal: " + danaPump.maxBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "Total daily max units: " + danaPump.maxDailyTotalUnits)
    }
}