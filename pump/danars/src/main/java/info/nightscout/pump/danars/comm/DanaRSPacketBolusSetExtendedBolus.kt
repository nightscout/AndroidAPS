package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRSPacketBolusSetExtendedBolus(
    injector: HasAndroidInjector,
    private var extendedAmount: Double = 0.0,
    private var extendedBolusDurationInHalfHours: Int = 0
) : DanaRSPacket(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus start : $extendedAmount U half-hours: $extendedBolusDurationInHalfHours")
    }

    override fun getRequestParams(): ByteArray {
        val extendedBolusRate = (extendedAmount * 100.0).toInt()
        val request = ByteArray(3)
        request[0] = (extendedBolusRate and 0xff).toByte()
        request[1] = (extendedBolusRate ushr 8 and 0xff).toByte()
        request[2] = (extendedBolusDurationInHalfHours and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override val friendlyName: String = "BOLUS__SET_EXTENDED_BOLUS"
}