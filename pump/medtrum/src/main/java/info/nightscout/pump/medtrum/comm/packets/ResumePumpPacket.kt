package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.enums.CommandType.RESUME_PUMP
import info.nightscout.pump.medtrum.extension.toByteArray

class ResumePumpPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = RESUME_PUMP.code
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode)
    }
}
