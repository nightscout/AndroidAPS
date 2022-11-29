package info.nightscout.pump.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.rx.logging.LTag

class DanaRSPacketGeneralGetUserTimeChangeFlag(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

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

    override val friendlyName: String = "REVIEW__GET_USER_TIME_CHANGE_FLAG"
}