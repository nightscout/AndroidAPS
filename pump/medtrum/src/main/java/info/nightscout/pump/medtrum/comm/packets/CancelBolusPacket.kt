package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.CANCEL_BOLUS

class CancelBolusPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = CANCEL_BOLUS.code
    }

    override fun getRequest(): ByteArray {
        // TODO: Get bolus type
        return byteArrayOf(opCode)
    }
}
