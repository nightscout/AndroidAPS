package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.danars.encryption.BleEncryption

class DanaRSPacketGeneralSetUserTimeChangeFlagClear(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
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

    override val friendlyName: String = "REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR"
}