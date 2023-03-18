package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.SET_TEMP_BASAL

class SetTempBasalPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = SET_TEMP_BASAL.code
        // TODO set expectedMinRespLength
    }

    override fun getRequest(): ByteArray {
        // TODO get temp basal settings
        return byteArrayOf(opCode)
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            // TODO Save basal
        }
        return success
    }
}
