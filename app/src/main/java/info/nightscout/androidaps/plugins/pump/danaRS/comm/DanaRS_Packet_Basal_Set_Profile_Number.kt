package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class DanaRS_Packet_Basal_Set_Profile_Number(
    private val aapsLogger: AAPSLogger,
    private var profileNumber: Int = 0
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER
        aapsLogger.debug(LTag.PUMPCOMM, "Setting profile number $profileNumber")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(1)
        request[0] = (profileNumber and 0xff).toByte()
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
        return "BASAL__SET_PROFILE_NUMBER"
    }
}