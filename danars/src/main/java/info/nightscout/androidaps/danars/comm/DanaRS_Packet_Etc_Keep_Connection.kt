package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Etc_Keep_Connection(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
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
        return "ETC__KEEP_CONNECTION"
    }
}