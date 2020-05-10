package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil

class DanaRS_Packet_History_Blood_Glucose @JvmOverloads constructor(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    dateUtil: DateUtil,
    from: Long = 0
) : DanaRS_Packet_History_(aapsLogger, rxBus, dateUtil, from) {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__BLOOD_GLUCOSE"
    }
}