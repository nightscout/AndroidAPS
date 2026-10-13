package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.SET_TIME
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class SetTimePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumTimeUtil: MedtrumTimeUtil

    init {
        opCode = SET_TIME.code
    }

    override fun getRequest(): ByteArray {
        val time = medtrumTimeUtil.getCurrentTimePumpSeconds()
        return byteArrayOf(opCode) + 2.toByte() + time.toByteArray(4)
    }
}
