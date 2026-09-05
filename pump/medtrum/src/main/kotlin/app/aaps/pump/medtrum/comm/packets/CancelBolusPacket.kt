package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.CANCEL_BOLUS
import app.aaps.core.interfaces.di.MetroMemberInjector

class CancelBolusPacket(injector: MetroMemberInjector) : MedtrumPacket(injector) {

    init {
        opCode = CANCEL_BOLUS.code
    }

    override fun getRequest(): ByteArray {
        // Bolus types:
        // 1 = normal
        // 2 = Extended
        // 3 = Combi
        val bolusType: Byte = 1 // Only support for normal bolus for now
        return byteArrayOf(opCode) + bolusType
    }
}
