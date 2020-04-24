package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class DanaRS_Packet_Bolus_Set_Extended_Bolus(
    private val aapsLogger: AAPSLogger,
    private var extendedAmount: Double = 0.0,
    private var extendedBolusDurationInHalfHours: Int = 0
) : DanaRS_Packet() {


    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus start : $extendedAmount U halfhours: $extendedBolusDurationInHalfHours")
    }

    override fun getRequestParams(): ByteArray {
        val extendedBolusRate = (extendedAmount * 100.0).toInt()
        val request = ByteArray(3)
        request[0] = (extendedBolusRate and 0xff).toByte()
        request[1] = (extendedBolusRate ushr 8 and 0xff).toByte()
        request[2] = (extendedBolusDurationInHalfHours and 0xff).toByte()
        return request
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
    }

    override fun getFriendlyName(): String {
        return "BOLUS__SET_EXTENDED_BOLUS"
    }
}