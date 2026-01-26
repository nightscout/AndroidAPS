package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import dagger.android.HasAndroidInjector

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
        BolusProgressData.delivered = deliveredInsulin
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
        rxBus.send(EventOverviewBolusProgress(ch, delivered = PumpInsulin(deliveredInsulin), id = danaPump.bolusingDetailedBolusInfo?.id))
    }
}