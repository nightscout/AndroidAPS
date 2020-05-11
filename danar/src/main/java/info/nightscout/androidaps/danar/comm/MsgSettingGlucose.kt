package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump

/**
 * Created by mike on 05.07.2016.
 */
class MsgSettingGlucose(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : MessageBase() {

    init {
        SetCommand(0x3209)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.units = intFromBuff(bytes, 0, 1)
        danaPump.easyBasalMode = intFromBuff(bytes, 1, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaPump.units == info.nightscout.androidaps.dana.DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Easy basal mode: " + danaPump.easyBasalMode)
    }
}