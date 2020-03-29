package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import info.nightscout.androidaps.utils.resources.ResourceHelper

open class DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {


    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
        val bolusingEvent = EventOverviewBolusProgress
        danaRPump.bolusStopped = true
        if (!danaRPump.bolusStopForced) {
            // delivery ended without user intervention
            danaRPump.bolusingTreatment?.insulin = danaRPump.bolusAmountToBeDelivered
            bolusingEvent.status = resourceHelper.gs(R.string.overview_bolusprogress_delivered)
            bolusingEvent.percent = 100
        } else {
            bolusingEvent.status = resourceHelper.gs(R.string.overview_bolusprogress_stoped)
        }
        rxBus.send(bolusingEvent)
    }

    override fun getFriendlyName(): String {
        return "BOLUS__SET_STEP_BOLUS_STOP"
    }
}