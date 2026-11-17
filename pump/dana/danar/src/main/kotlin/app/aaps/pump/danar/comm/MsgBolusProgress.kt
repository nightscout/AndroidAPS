package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpInsulin

class MsgBolusProgress(
    injector: MetroMemberInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0202)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val remaining = intFromBuff(bytes, 0, 2) / 100.0
        // The pump can go on with a bolus the driver no longer follows, for example after a stop that got no answer.
        // That progress used to end the whole app here. The pump history counts that bolus.
        val bolusing = danaPump.bolusingDetailedBolusInfo
        if (bolusing == null) {
            aapsLogger.debug(LTag.PUMPCOMM, "Bolus progress of a bolus the driver does not follow, remaining: $remaining")
            return
        }
        val deliveredInsulin = bolusing.insulin - remaining
        danaPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
        bolusProgressData.updateProgress(PumpInsulin(deliveredInsulin))
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
    }
}
