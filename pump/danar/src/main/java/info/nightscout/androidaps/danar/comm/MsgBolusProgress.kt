package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
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
        val deliveredInsulin = danaPump.bolusAmountToBeDelivered - intFromBuff(bytes, 0, 2) / 100.0
        danaPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
        danaPump.bolusingTreatment?.insulin = deliveredInsulin
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.status = rh.gs(app.aaps.core.ui.R.string.bolus_delivering, deliveredInsulin)
        bolusingEvent.t = danaPump.bolusingTreatment
        bolusingEvent.percent = min((deliveredInsulin / danaPump.bolusAmountToBeDelivered * 100).toInt(), 100)
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
        rxBus.send(bolusingEvent)
    }
}