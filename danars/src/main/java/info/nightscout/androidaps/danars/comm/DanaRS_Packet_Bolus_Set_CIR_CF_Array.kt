package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Bolus_Set_CIR_CF_Array(
    injector: HasAndroidInjector,
    private var cir01: Int = 0,
    private var cir02: Int = 0,
    private var cir03: Int = 0,
    private var cir04: Int = 0,
    private var cir05: Int = 0,
    private var cir06: Int = 0,
    private var cir07: Int = 0,
    private var cf01: Int = 0,
    private var cf02: Int = 0,
    private var cf03: Int = 0,
    private var cf04: Int = 0,
    private var cf05: Int = 0,
    private var cf06: Int = 0,
    private var cf07: Int = 0
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_CIR_CF_ARRAY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(28)
        request[0] = (cir01 and 0xff).toByte()
        request[1] = (cir01 ushr 8 and 0xff).toByte()
        request[2] = (cir02 and 0xff).toByte()
        request[3] = (cir02 ushr 8 and 0xff).toByte()
        request[4] = (cir03 and 0xff).toByte()
        request[5] = (cir03 ushr 8 and 0xff).toByte()
        request[6] = (cir04 and 0xff).toByte()
        request[7] = (cir04 ushr 8 and 0xff).toByte()
        request[8] = (cir05 and 0xff).toByte()
        request[9] = (cir05 ushr 8 and 0xff).toByte()
        request[10] = (cir06 and 0xff).toByte()
        request[11] = (cir06 ushr 8 and 0xff).toByte()
        request[12] = (cir07 and 0xff).toByte()
        request[13] = (cir07 ushr 8 and 0xff).toByte()
        request[14] = (cf01 and 0xff).toByte()
        request[15] = (cf01 ushr 8 and 0xff).toByte()
        request[16] = (cf02 and 0xff).toByte()
        request[17] = (cf02 ushr 8 and 0xff).toByte()
        request[18] = (cf03 and 0xff).toByte()
        request[19] = (cf03 ushr 8 and 0xff).toByte()
        request[20] = (cf04 and 0xff).toByte()
        request[21] = (cf04 ushr 8 and 0xff).toByte()
        request[22] = (cf05 and 0xff).toByte()
        request[23] = (cf05 ushr 8 and 0xff).toByte()
        request[24] = (cf06 and 0xff).toByte()
        request[25] = (cf06 ushr 8 and 0xff).toByte()
        request[26] = (cf07 and 0xff).toByte()
        request[27] = (cf07 ushr 8 and 0xff).toByte()
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
        return "BOLUS__SET_CIR_CF_ARRAY"
    }
}