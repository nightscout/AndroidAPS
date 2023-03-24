package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.enums.CommandType.SET_BOLUS_MOTOR

class SetBolusMotorPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    init {
        opCode = SET_BOLUS_MOTOR.code
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode) + 0.toByte()
    }
}
