package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.enums.CommandType.CANCEL_BOLUS

class CancelBolusPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

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
