package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRSPacketHistoryBloodGlucose(
    injector: HasAndroidInjector,
    from: Long = 0
) : DanaRSPacketHistory(injector, from) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override val friendlyName: String = "REVIEW__BLOOD_GLUCOSE"
}