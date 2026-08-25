package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpInsulin
import dagger.android.HasAndroidInjector
import kotlin.math.min

class MsgBolusProgress(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0202)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val deliveredInsulin = danaPump.bolusingDetailedBolusInfo!!.insulin - intFromBuff(bytes, 0, 2) / 100.0
        danaPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
        bolusProgressData.updateProgress(PumpInsulin(deliveredInsulin))
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
    }
}
