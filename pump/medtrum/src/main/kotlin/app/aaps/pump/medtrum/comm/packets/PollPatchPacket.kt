package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.POLL_PATCH
import dagger.android.HasAndroidInjector

class PollPatchPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = POLL_PATCH.code
    }
}
