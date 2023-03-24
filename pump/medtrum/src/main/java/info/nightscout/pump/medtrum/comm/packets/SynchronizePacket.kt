package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.enums.CommandType.SYNCHRONIZE
import info.nightscout.pump.medtrum.extension.toInt

class SynchronizePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    var state: Int = 0
    var dataFieldsPresent: Int = 0
    var syncData: ByteArray = byteArrayOf()

    companion object {

        private const val RESP_STATE_START = 6
        private const val RESP_STATE_END = RESP_STATE_START + 1
        private const val RESP_FIELDS_START = 7
        private const val RESP_FIELDS_END = RESP_FIELDS_START + 2
        private const val RESP_SYNC_DATA_START = 9
    }

    init {
        opCode = SYNCHRONIZE.code
        expectedMinRespLength = RESP_SYNC_DATA_START + 1
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            state = data.copyOfRange(RESP_STATE_START, RESP_STATE_END).toInt()
            dataFieldsPresent = data.copyOfRange(RESP_FIELDS_START, RESP_FIELDS_END).toInt()
            syncData = data.copyOfRange(RESP_SYNC_DATA_START, data.size)
        }

        return success
    }
}
