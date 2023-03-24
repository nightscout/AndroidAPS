package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.comm.enums.CommandType.SET_BASAL_PROFILE
import javax.inject.Inject

class SetBasalProfilePacket(injector: HasAndroidInjector, private val basalProfile: ByteArray) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump

    init {
        opCode = SET_BASAL_PROFILE.code
    }

    override fun getRequest(): ByteArray {
        val basalType: Byte = 1 // Fixed to normal basal
        return byteArrayOf(opCode) + basalType + basalProfile
    }
}
