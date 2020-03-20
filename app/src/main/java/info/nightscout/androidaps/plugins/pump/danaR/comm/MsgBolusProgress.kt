package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlin.math.min

class MsgBolusProgress(
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val rxBus: RxBusWrapper,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x0202)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val deliveredInsulin = danaRPump.bolusAmountToBeDelivered - intFromBuff(bytes, 0, 2) / 100.0
        danaRPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
        danaRPump.bolusingTreatment?.insulin = deliveredInsulin
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.status = resourceHelper.gs(R.string.bolusdelivering, deliveredInsulin)
        bolusingEvent.t = danaRPump.bolusingTreatment
        bolusingEvent.percent = min((deliveredInsulin / danaRPump.bolusAmountToBeDelivered * 100).toInt(), 100)
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
        rxBus.send(bolusingEvent)
    }
}