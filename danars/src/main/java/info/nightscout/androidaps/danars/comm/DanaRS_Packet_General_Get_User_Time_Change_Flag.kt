package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_General_Get_User_Time_Change_Flag(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG
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