package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.SET_TIME
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import app.aaps.core.interfaces.di.MetroMemberInjector
import javax.inject.Inject

class SetTimePacket(injector: MetroMemberInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumTimeUtil: MedtrumTimeUtil

    init {
        opCode = SET_TIME.code
    }

    override fun getRequest(): ByteArray {
        val time = medtrumTimeUtil.getCurrentTimePumpSeconds()
        return byteArrayOf(opCode) + 2.toByte() + time.toByteArray(4)
    }
}
