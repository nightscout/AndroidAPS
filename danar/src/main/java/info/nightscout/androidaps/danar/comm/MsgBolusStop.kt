package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.R
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress

class MsgBolusStop(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0101)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "Messsage received")
        val bolusingEvent = EventOverviewBolusProgress
        danaPump.bolusStopped = true
        if (!danaPump.bolusStopForced) {
            danaPump.bolusingTreatment?.insulin = danaPump.bolusAmountToBeDelivered
            bolusingEvent.status = rh.gs(R.string.overview_bolusprogress_delivered)
            bolusingEvent.percent = 100
        } else {
            bolusingEvent.status = rh.gs(R.string.overview_bolusprogress_stoped)
        }
        rxBus.send(bolusingEvent)
    }
}