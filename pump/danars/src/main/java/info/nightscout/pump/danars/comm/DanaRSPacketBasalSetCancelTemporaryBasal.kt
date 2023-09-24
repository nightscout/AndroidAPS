package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRSPacketBasalSetCancelTemporaryBasal(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL
        aapsLogger.debug(LTag.PUMPCOMM, "Canceling temp basal")
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

    override val friendlyName: String = "BASAL__CANCEL_TEMPORARY_BASAL"
}