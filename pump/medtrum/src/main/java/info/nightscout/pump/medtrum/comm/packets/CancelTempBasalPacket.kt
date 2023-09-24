package info.nightscout.pump.medtrum.comm.packets

import app.aaps.core.interfaces.utils.DateUtil
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.comm.enums.CommandType.CANCEL_TEMP_BASAL
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.pump.medtrum.extension.toLong
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import javax.inject.Inject

class CancelTempBasalPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var dateUtil: DateUtil

    companion object {

        private const val RESP_BASAL_TYPE_START = 6
        private const val RESP_BASAL_TYPE_END = RESP_BASAL_TYPE_START + 1
        private const val RESP_BASAL_RATE_START = RESP_BASAL_TYPE_END
        private const val RESP_BASAL_RATE_END = RESP_BASAL_RATE_START + 2
        private const val RESP_BASAL_SEQUENCE_START = RESP_BASAL_RATE_END
        private const val RESP_BASAL_SEQUENCE_END = RESP_BASAL_SEQUENCE_START + 2
        private const val RESP_BASAL_PATCH_ID_START = RESP_BASAL_SEQUENCE_END
        private const val RESP_BASAL_PATCH_ID_END = RESP_BASAL_PATCH_ID_START + 2
        private const val RESP_BASAL_START_TIME_START = RESP_BASAL_PATCH_ID_END
        private const val RESP_BASAL_START_TIME_END = RESP_BASAL_START_TIME_START + 4
    }

    init {
        opCode = CANCEL_TEMP_BASAL.code
        expectedMinRespLength = RESP_BASAL_START_TIME_END
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            val basalType = enumValues<BasalType>()[data.copyOfRange(RESP_BASAL_TYPE_START, RESP_BASAL_TYPE_END).toInt()]
            val basalRate = data.copyOfRange(RESP_BASAL_RATE_START, RESP_BASAL_RATE_END).toInt() * 0.05
            val basalSequence = data.copyOfRange(RESP_BASAL_SEQUENCE_START, RESP_BASAL_SEQUENCE_END).toInt()
            val basalPatchId = data.copyOfRange(RESP_BASAL_PATCH_ID_START, RESP_BASAL_PATCH_ID_END).toLong()
            val basalStartTime = MedtrumTimeUtil().convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_BASAL_START_TIME_START, RESP_BASAL_START_TIME_END).toLong())

            medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime)
        }
        return success
    }
}
