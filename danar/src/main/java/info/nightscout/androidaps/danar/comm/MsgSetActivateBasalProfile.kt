package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag

class MsgSetActivateBasalProfile(
    injector: HasAndroidInjector,
    index: Byte
) : MessageBase(injector) {

    // index 0-3
    init {
        SetCommand(0x330C)
        AddParamByte(index)
        aapsLogger.debug(LTag.PUMPCOMM, "Activate basal profile: $index")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Activate basal profile result: $result FAILED!!!")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Activate basal profile result: $result")
        }
    }
}