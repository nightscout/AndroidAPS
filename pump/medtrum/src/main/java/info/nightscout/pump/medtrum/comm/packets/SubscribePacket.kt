package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.packets.CommandType.SUBSCRIBE
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil

class SubscribePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = SUBSCRIBE.code
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode) + 4095.toByteArray(2)
    }
}
