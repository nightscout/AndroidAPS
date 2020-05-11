package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgBolusStop(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaPump: DanaPump
) : MessageBase() {

    init {
        SetCommand(0x0101)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "Messsage received")
        val bolusingEvent = EventOverviewBolusProgress
        danaPump.bolusStopped = true
        if (!danaPump.bolusStopForced) {
            danaPump.bolusingTreatment?.insulin = danaPump.bolusAmountToBeDelivered
            bolusingEvent.status = resourceHelper.gs(R.string.overview_bolusprogress_delivered)
            bolusingEvent.percent = 100
        } else {
            bolusingEvent.status = resourceHelper.gs(R.string.overview_bolusprogress_stoped)
        }
        rxBus.send(bolusingEvent)
    }
}