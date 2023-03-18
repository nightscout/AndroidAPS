package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.STOP_PATCH

class StopPatchPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = STOP_PATCH.code
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            // TODO
        }
        return success
    }
}
