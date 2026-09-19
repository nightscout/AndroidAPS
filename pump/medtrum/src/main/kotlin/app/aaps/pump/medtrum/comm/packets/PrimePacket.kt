package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.PRIME
import app.aaps.core.interfaces.di.MetroMemberInjector

class PrimePacket(injector: MetroMemberInjector) : MedtrumPacket(injector) {

    init {
        opCode = PRIME.code
    }
}
