package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_History_All_History(
    injector: HasAndroidInjector,
    from: Long = 0
) : DanaRS_Packet_History_(injector, from) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__ALL_HISTORY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__ALL_HISTORY"
    }
}