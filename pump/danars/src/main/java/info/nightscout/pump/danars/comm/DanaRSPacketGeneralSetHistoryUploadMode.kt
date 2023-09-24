package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRSPacketGeneralSetHistoryUploadMode(
    injector: HasAndroidInjector,
    private var mode: Int = 0
) : DanaRSPacket(injector) {

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
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override val friendlyName: String = "REVIEW__SET_HISTORY_UPLOAD_MODE"
}