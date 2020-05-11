package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Basal_Set_Profile_Number(
    injector: HasAndroidInjector,
    private var profileNumber: Int = 0
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER
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