package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlin.math.min

class DanaRS_Packet_Notify_Delivery_Rate_Display(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {


    init {
        type = BleEncryption.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY
    }

    override fun handleMessage(data: ByteArray) {
        val deliveredInsulin = byteArrayToInt(getBytes(data, DATA_START, 2)) / 100.0
        danaRPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
        danaRPump.bolusingTreatment?.insulin = deliveredInsulin
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.status = resourceHelper.gs(R.string.bolusdelivering, deliveredInsulin)
        bolusingEvent.t = danaRPump.bolusingTreatment
        bolusingEvent.percent = min((deliveredInsulin / danaRPump.bolusAmountToBeDelivered * 100).toInt(), 100)
        failed = bolusingEvent.percent < 100
        rxBus.send(bolusingEvent)
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
    }

    override fun getFriendlyName(): String {
        return "NOTIFY__DELIVERY_RATE_DISPLAY"
    }
}