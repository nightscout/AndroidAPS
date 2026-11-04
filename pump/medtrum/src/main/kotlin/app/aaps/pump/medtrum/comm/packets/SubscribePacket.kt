package app.aaps.pump.medtrum.comm.packets

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.SUBSCRIBE
import app.aaps.pump.medtrum.extension.toByteArray

class SubscribePacket(injector: MetroMemberInjector) : MedtrumPacket(injector) {

    init {
        opCode = SUBSCRIBE.code
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode) + 4095.toByteArray(2)
    }
}
