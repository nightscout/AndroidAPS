package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption

class DanaRS_Packet_Review_Get_Pump_Dec_Ratio(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_DEC_RATIO
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        danaRPump.decRatio = intFromBuff(data, 0, 1) * 5
        failed = false
        aapsLogger.debug(LTag.PUMPCOMM, "Dec ratio: ${danaRPump.decRatio}%")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_PUMP_DEC_RATIO"
    }
}