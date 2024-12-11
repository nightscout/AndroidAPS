package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.enums.CommandType.POLL_PATCH

class PollPatchPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = POLL_PATCH.code
    }
}
