package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class DanaRS_Packet_Basal_Set_Profile_Basal_Rate(
    private val aapsLogger: AAPSLogger,
    private var profileNumber: Int,
    private var profileBasalRate: Array<Double>
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "Setting new basal rates for profile $profileNumber")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(49)
        request[0] = (profileNumber and 0xff).toByte()
        var i = 0
        val size = 24
        while (i < size) {
            val rate = (profileBasalRate[i] * 100.0).toInt()
            request[1 + i * 2] = (rate and 0xff).toByte()
            request[2 + i * 2] = (rate ushr 8 and 0xff).toByte()
            i++
        }
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
        return "BASAL__SET_PROFILE_BASAL_RATE"
    }
}