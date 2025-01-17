package app.aaps.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.POLL_PATCH

class PollPatchPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = POLL_PATCH.code
    }
}
