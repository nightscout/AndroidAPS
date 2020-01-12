package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class DanaRS_Packet_General_Get_User_Time_Change_Flag(
    private val aapsLogger: AAPSLogger
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 3) {
            failed = true
            return
        } else failed = false
        val dataIndex = DATA_START
        val dataSize = 1
        val userTimeChangeFlag = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        aapsLogger.debug(LTag.PUMPCOMM, "UserTimeChangeFlag: $userTimeChangeFlag")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_USER_TIME_CHANGE_FLAG"
    }
}