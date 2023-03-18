package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.CANCEL_TEMP_BASAL

class CancelTempBasalPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = CANCEL_TEMP_BASAL.code
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            // TODO Save basal
        }
        return success
    }
}
