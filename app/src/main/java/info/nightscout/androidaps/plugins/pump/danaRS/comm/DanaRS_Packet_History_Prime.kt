package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper

class DanaRS_Packet_History_Prime @JvmOverloads constructor(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    from: Long = 0
) : DanaRS_Packet_History_(aapsLogger, rxBus, from) {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__PRIME
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__PRIME"
    }
}