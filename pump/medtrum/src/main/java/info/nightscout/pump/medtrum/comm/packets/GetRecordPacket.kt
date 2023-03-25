package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.comm.enums.CommandType.GET_RECORD
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.pump.medtrum.extension.toLong
import javax.inject.Inject

class GetRecordPacket(injector: HasAndroidInjector, private val recordIndex: Int) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump

    companion object {

        private const val RESP_RECORD_HEADER_START = 6
        private const val RESP_RECORD_HEADER_END = RESP_RECORD_HEADER_START + 1
        private const val RESP_RECORD_UNKNOWN_START = RESP_RECORD_HEADER_END
        private const val RESP_RECORD_UNKNOWN_END = RESP_RECORD_UNKNOWN_START + 1
        private const val RESP_RECORD_TYPE_START = RESP_RECORD_UNKNOWN_END
        private const val RESP_RECORD_TYPE_END = RESP_RECORD_TYPE_START + 1
        private const val RESP_RECORD_SERIAL_START = RESP_RECORD_TYPE_END
        private const val RESP_RECORD_SERIAL_END = RESP_RECORD_SERIAL_START + 4
        private const val RESP_RECORD_PATCHID_START = RESP_RECORD_SERIAL_END
        private const val RESP_RECORD_PATCHID_END = RESP_RECORD_PATCHID_START + 2
        private const val RESP_RECORD_DATA_START = RESP_RECORD_PATCHID_END
    }

    init {
        opCode = GET_RECORD.code
        expectedMinRespLength = RESP_RECORD_DATA_START
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode) + recordIndex.toByteArray(2) + medtrumPump.patchId.toByteArray(2)
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            val recordHeader = data.copyOfRange(RESP_RECORD_HEADER_START, RESP_RECORD_HEADER_END).toInt()
            val recordUnknown = data.copyOfRange(RESP_RECORD_UNKNOWN_START, RESP_RECORD_UNKNOWN_END).toInt()
            val recordType = data.copyOfRange(RESP_RECORD_TYPE_START, RESP_RECORD_TYPE_END).toInt()
            val recordSerial = data.copyOfRange(RESP_RECORD_SERIAL_START, RESP_RECORD_SERIAL_END).toLong()
            val recordPatchId = data.copyOfRange(RESP_RECORD_PATCHID_START, RESP_RECORD_PATCHID_END).toInt()

            // TODO Handle history records
        }

        return success
    }
}