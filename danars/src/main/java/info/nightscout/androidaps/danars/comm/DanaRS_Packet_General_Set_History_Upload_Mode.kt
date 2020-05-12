package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_General_Set_History_Upload_Mode(
    injector: HasAndroidInjector,
    private var mode: Int = 0
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE
        aapsLogger.debug(LTag.PUMPCOMM, "New message: mode: $mode")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(1)
        request[0] = (mode and 0xff).toByte()
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
        return "REVIEW__SET_HISTORY_UPLOAD_MODE"
    }
}