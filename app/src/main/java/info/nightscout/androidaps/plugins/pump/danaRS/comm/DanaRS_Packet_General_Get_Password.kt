package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class DanaRS_Packet_General_Get_Password(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 2) { // returned data size is too small
            failed = true
            return
        } else {
            failed = false
        }
        var pass: Int = (data[DATA_START + 1].toInt() and 0x000000FF shl 8) + (data[DATA_START + 0].toInt() and 0x000000FF)
        pass = pass xor 3463
        danaRPump.rsPassword = Integer.toHexString(pass)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump password: " + danaRPump.rsPassword)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_PASSWORD"
    }
}