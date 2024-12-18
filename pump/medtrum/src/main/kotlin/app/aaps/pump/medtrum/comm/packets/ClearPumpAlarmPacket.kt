package app.aaps.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.CLEAR_ALARM
import app.aaps.pump.medtrum.extension.toByteArray

class ClearPumpAlarmPacket(injector: HasAndroidInjector, private val clearType: Int) : MedtrumPacket(injector) {

    init {
        opCode = CLEAR_ALARM.code
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode) + clearType.toByteArray(2)
    }
}
