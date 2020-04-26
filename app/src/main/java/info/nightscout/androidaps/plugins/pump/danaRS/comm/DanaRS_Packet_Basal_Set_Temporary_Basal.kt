package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

open class DanaRS_Packet_Basal_Set_Temporary_Basal(
    private val aapsLogger: AAPSLogger,
    private var temporaryBasalRatio: Int = 0,
    private var temporaryBasalDuration: Int = 0
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL
        aapsLogger.debug(LTag.PUMPCOMM, "Setting temporary basal of $temporaryBasalRatio% for $temporaryBasalDuration hours")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(2)
        request[0] = (temporaryBasalRatio and 0xff).toByte()
        request[1] = (temporaryBasalDuration and 0xff).toByte()
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
        return "BASAL__SET_TEMPORARY_BASAL"
    }
}