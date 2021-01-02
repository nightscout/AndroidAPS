package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Bolus_Set_Initial_Bolus(
    injector: HasAndroidInjector,
    private var bolusRate01: Int = 0,
    private var bolusRate02: Int = 0,
    private var bolusRate03: Int = 0,
    private var bolusRate04: Int = 0
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(8)
        request[0] = (bolusRate01 and 0xff).toByte()
        request[1] = (bolusRate01 ushr 8 and 0xff).toByte()
        request[2] = (bolusRate02 and 0xff).toByte()
        request[3] = (bolusRate02 ushr 8 and 0xff).toByte()
        request[4] = (bolusRate03 and 0xff).toByte()
        request[5] = (bolusRate03 ushr 8 and 0xff).toByte()
        request[6] = (bolusRate04 and 0xff).toByte()
        request[7] = (bolusRate04 ushr 8 and 0xff).toByte()
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
        return "BOLUS__SET_BOLUS_RATE"
    }
}