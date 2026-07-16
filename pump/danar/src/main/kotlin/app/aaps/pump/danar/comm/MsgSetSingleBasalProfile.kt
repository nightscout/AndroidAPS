package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgSetSingleBasalProfile(
    injector: HasAndroidInjector,
    values: Array<Double>
) : MessageBase(injector) {

    // index 0-3
    init {
        setCommand(0x3302)
        for (i in 0..23) {
            addParamInt((values[i] * 100).toInt())
        }
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Set basal profile result: $result ERROR!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set basal profile result: $result")
        }
        // Notifications (OK / FAILED) are owned centrally; the `failed` flag above drives the setNewBasalProfile
        // return value (DanaRKoreanExecutionService.updateBasalsInPump -> AbstractDanaRPlugin -> onProfileChanged).
    }
}