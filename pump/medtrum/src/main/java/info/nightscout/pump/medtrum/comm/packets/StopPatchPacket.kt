package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.pump.medtrum.comm.enums.CommandType.STOP_PATCH
import info.nightscout.pump.medtrum.extension.toLong
import javax.inject.Inject

class StopPatchPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump

    companion object {

        private const val RESP_STOP_SEQUENCE_START = 6
        private const val RESP_STOP_SEQUENCE_END = RESP_STOP_SEQUENCE_START + 2
        private const val RESP_STOP_PATCH_ID_START = RESP_STOP_SEQUENCE_END
        private const val RESP_STOP_PATCH_ID_END = RESP_STOP_PATCH_ID_START + 2
    }

    init {
        opCode = STOP_PATCH.code
        expectedMinRespLength = RESP_STOP_PATCH_ID_END
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            val stopSequence = data.copyOfRange(RESP_STOP_SEQUENCE_START, RESP_STOP_SEQUENCE_END).toInt()
            val stopPatchId = data.copyOfRange(RESP_STOP_PATCH_ID_START, RESP_STOP_PATCH_ID_END).toLong()

            medtrumPump.handleStopStatusUpdate(stopSequence, stopPatchId)
        }
        return success
    }
}
