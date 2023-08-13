package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.enums.CommandType.PRIME

class PrimePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = PRIME.code
    }
}
