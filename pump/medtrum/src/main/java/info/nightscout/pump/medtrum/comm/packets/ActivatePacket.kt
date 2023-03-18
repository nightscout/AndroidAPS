package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.ACTIVATE

class ActivatePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = ACTIVATE.code
    }

    override fun getRequest(): ByteArray {
        // TODO get activation commands
        return byteArrayOf(opCode)
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            // TODO
        }

        return success
    }
}
