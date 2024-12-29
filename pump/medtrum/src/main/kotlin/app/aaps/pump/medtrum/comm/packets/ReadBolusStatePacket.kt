package app.aaps.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.READ_BOLUS_STATE

class ReadBolusStatePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {
    // UNUSED
    // Bolus sync is currently done by getting the records and syncing then with AAPS pumpSync there

    var bolusData: ByteArray = byteArrayOf()

    companion object {

        private const val RESP_BOLUS_DATA_START = 6
    }

    init {
        opCode = READ_BOLUS_STATE.code
        expectedMinRespLength = RESP_BOLUS_DATA_START + 1
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            // UNUSED
            bolusData = data.copyOfRange(RESP_BOLUS_DATA_START, data.size)
        }

        return success
    }
}
