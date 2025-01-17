package app.aaps.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.SET_TIME
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.util.MedtrumTimeUtil

class SetTimePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = SET_TIME.code
    }

    override fun getRequest(): ByteArray {
        val time = MedtrumTimeUtil().getCurrentTimePumpSeconds()
        return byteArrayOf(opCode) + 2.toByte() + time.toByteArray(4)
    }
}
