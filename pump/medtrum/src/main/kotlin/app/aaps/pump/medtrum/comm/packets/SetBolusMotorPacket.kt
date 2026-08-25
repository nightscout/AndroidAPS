package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.comm.enums.CommandType.SET_BOLUS_MOTOR
import dagger.android.HasAndroidInjector

class SetBolusMotorPacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    // UNUSED in our driver

    init {
        opCode = SET_BOLUS_MOTOR.code
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode) + 0.toByte()
    }
}
