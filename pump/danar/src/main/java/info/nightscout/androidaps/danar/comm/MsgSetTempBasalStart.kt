package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgSetTempBasalStart(
    injector: HasAndroidInjector,
    private var percent: Int,
    private var durationInHours: Int
) : MessageBase(injector) {

    init {
        setCommand(0x0401)

        //HARDCODED LIMITS
        if (percent < 0) percent = 0
        if (percent > 200) percent = 200
        if (durationInHours < 1) durationInHours = 1
        if (durationInHours > 24) durationInHours = 24
        addParamByte((percent and 255).toByte())
        addParamByte((durationInHours and 255).toByte())
        aapsLogger.debug(LTag.PUMPCOMM, "Temp basal start percent: $percent duration hours: $durationInHours")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Set temp basal start result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set temp basal start result: $result")
        }
    }
}