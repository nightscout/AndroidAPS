package info.nightscout.pump.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.rx.logging.LTag

class DanaRSPacketBasalSetProfileNumber(
    injector: HasAndroidInjector,
    private var profileNumber: Int = 0
) : DanaRSPacket(injector) {

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
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override val friendlyName: String = "BASAL__SET_PROFILE_NUMBER"
}