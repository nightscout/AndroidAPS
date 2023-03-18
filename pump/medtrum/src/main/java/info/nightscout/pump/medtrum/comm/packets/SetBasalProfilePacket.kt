package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.SET_BASAL_PROFILE

class SetBasalProfilePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = SET_BASAL_PROFILE.code
    }

    override fun getRequest(): ByteArray {
        // TODO get basal profile settings
        return byteArrayOf(opCode)
    }
}
