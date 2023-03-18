package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.SET_BOLUS

class SetBolusPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = SET_BOLUS.code
    }

    override fun getRequest(): ByteArray {
        // TODO get bolus settings
        return byteArrayOf(opCode) + 0.toByte()
    }
}
