package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Bolus_Set_Dual_Bolus(
    injector: HasAndroidInjector,
    private var amount: Double = 0.0,
    private var extendedAmount: Double = 0.0,
    private var extendedBolusDurationInHalfHours: Int = 0
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_DUAL_BOLUS
        aapsLogger.debug(LTag.PUMPCOMM, "Dual bolus start : $amount U extended: $extendedAmount U halfhours: $extendedBolusDurationInHalfHours")
    }

    override fun getRequestParams(): ByteArray {
        val stepBolusRate = (amount / 100.0).toInt()
        val extendedBolusRate = (extendedAmount / 100.0).toInt()
        val request = ByteArray(5)
        request[0] = (stepBolusRate and 0xff).toByte()
        request[1] = (stepBolusRate ushr 8 and 0xff).toByte()
        request[2] = (extendedBolusRate and 0xff).toByte()
        request[3] = (extendedBolusRate ushr 8 and 0xff).toByte()
        request[4] = (extendedBolusDurationInHalfHours and 0xff).toByte()
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
        return "BOLUS__SET_DUAL_BOLUS"
    }
}