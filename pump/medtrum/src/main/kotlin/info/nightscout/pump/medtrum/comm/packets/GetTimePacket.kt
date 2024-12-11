package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.comm.enums.CommandType.GET_TIME
import info.nightscout.pump.medtrum.extension.toLong
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import javax.inject.Inject

class GetTimePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump

    companion object {

        private const val RESP_TIME_START = 6
        private const val RESP_TIME_END = RESP_TIME_START + 4
    }

    init {
        opCode = GET_TIME.code
        expectedMinRespLength = RESP_TIME_END
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            val time = MedtrumTimeUtil().convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_TIME_START, RESP_TIME_END).toLong())
            medtrumPump.lastTimeReceivedFromPump = time
        }

        return success
    }
}
