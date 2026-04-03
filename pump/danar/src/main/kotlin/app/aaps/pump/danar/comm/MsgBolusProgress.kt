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
        val isPriming = bolusProgressData.state.value?.isPriming == true
        val insulin = bolusProgressData.state.value?.insulin ?: 0.0
        val delivered = ch.fromPump(PumpInsulin(deliveredInsulin), isPriming)
        val percent = if (insulin > 0) min((delivered / insulin * 100).toInt(), 100) else 0
        val status = ch.bolusProgressString(PumpInsulin(deliveredInsulin), isPriming)
        bolusProgressData.updateProgress(percent, status, deliveredInsulin)
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
    }
}
