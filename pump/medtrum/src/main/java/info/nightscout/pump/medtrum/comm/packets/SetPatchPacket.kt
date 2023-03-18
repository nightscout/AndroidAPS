package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.SET_PATCH
import info.nightscout.pump.medtrum.extension.toByteArray

class SetPatchPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = SET_PATCH.code
    }

    override fun getRequest(): ByteArray {
        // TODO get patch settings
        return byteArrayOf(opCode)
    }
}
