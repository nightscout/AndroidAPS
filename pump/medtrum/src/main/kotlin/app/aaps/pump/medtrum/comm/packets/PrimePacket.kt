package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.PRIME
import dagger.android.HasAndroidInjector

class PrimePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = PRIME.code
    }
}
