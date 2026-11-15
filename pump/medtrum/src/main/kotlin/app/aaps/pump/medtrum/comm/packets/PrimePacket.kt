package app.aaps.pump.medtrum.comm.packets

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.PRIME

class PrimePacket(injector: MetroMemberInjector) : MedtrumPacket(injector) {

    init {
        opCode = PRIME.code
    }
}
