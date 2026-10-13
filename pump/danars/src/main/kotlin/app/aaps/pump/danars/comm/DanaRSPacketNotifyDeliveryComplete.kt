package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketNotifyDeliveryComplete @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        type = BleEncryption.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val deliveredInsulin = byteArrayToInt(getBytes(data, DATA_START, 2)) / 100.0
        BolusProgressData.delivered = deliveredInsulin
        danaPump.bolusDone = true
        rxBus.send(EventOverviewBolusProgress(rh, delivered = deliveredInsulin, id = danaPump.bolusingDetailedBolusInfo?.id))
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin: $deliveredInsulin")
    }

    override val friendlyName: String = "NOTIFY__DELIVERY_COMPLETE"
}