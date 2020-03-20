package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlin.math.min

class DanaRS_Packet_Notify_Delivery_Complete(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRSPlugin: DanaRSPlugin
) : DanaRS_Packet() {

    init {
        type = BleCommandUtil.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val deliveredInsulin = byteArrayToInt(getBytes(data, DATA_START, 2)) / 100.0
        danaRSPlugin.bolusingTreatment.insulin = deliveredInsulin
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.status = resourceHelper.gs(R.string.bolusdelivering, deliveredInsulin)
        bolusingEvent.t = danaRSPlugin.bolusingTreatment
        bolusingEvent.percent = min((deliveredInsulin / danaRSPlugin.bolusAmountToBeDelivered * 100).toInt(), 100)
        danaRSPlugin.bolusDone = true
        rxBus.send(bolusingEvent)
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin: $deliveredInsulin")
    }

    override fun getFriendlyName(): String {
        return "NOTIFY__DELIVERY_COMPLETE"
    }
}