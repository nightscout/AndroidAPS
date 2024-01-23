package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgSetActivateBasalProfile(
    injector: HasAndroidInjector,
    index: Byte
) : MessageBase(injector) {

    // index 0-3
    init {
        setCommand(0x330C)
        addParamByte(index)
        aapsLogger.debug(LTag.PUMPCOMM, "Activate basal profile: $index")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Activate basal profile result: $result ERROR!!!")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Activate basal profile result: $result")
        }
    }
}