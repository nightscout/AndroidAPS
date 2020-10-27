package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Basal_Set_Basal_Rate(
    injector: HasAndroidInjector,
    private var profileBasalRate: Array<Double>
) : DanaRS_Packet(injector) {


    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_BASAL_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "Setting new basal rates")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(48)
        var i = 0
        val size = 24
        while (i < size) {
            val rate = (profileBasalRate[i] * 100.0).toInt()
            request[0 + i * 2] = (rate and 0xff).toByte()
            request[1 + i * 2] = (rate ushr 8 and 0xff).toByte()
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
        return "BASAL__SET_BASAL_RATE"
    }
}