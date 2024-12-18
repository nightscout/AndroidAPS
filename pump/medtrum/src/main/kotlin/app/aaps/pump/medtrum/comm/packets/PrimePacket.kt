package app.aaps.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.PRIME

class PrimePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = PRIME.code
    }
}
