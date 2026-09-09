package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.POLL_PATCH
import app.aaps.core.interfaces.di.MetroMemberInjector

class PollPatchPacket(injector: MetroMemberInjector) : MedtrumPacket(injector) {

    init {
        opCode = POLL_PATCH.code
    }
}
