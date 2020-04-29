package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper

class DanaRS_Packet_History_Suspend @JvmOverloads constructor(
    injector: HasAndroidInjector,
    from: Long = 0
) : DanaRS_Packet_History_(injector, from) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__SUSPEND
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__SUSPEND"
    }
}