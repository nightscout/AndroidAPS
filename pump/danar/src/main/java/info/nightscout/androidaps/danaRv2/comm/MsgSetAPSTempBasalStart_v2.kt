package info.nightscout.androidaps.danaRv2.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.rx.logging.LTag


@Suppress("UNUSED_PARAMETER")
class MsgSetAPSTempBasalStart_v2(
    injector: HasAndroidInjector,
    private var percent: Int,
    fifteenMinutes: Boolean,
    thirtyMinutes: Boolean
) : MessageBase(injector) {

    val PARAM30MIN = 160
    val PARAM15MIN = 150

    init {
        setCommand(0xE002)
        //HARDCODED LIMITS
        if (percent < 0) percent = 0
        if (percent > 500) percent = 500
        addParamInt(percent)
        if (thirtyMinutes && percent <= 200) { // 30 min is allowed up to 200%
            addParamByte(PARAM30MIN.toByte())
            aapsLogger.debug(LTag.PUMPCOMM, "APS Temp basal start percent: $percent duration 30 min")
        } else {
            addParamByte(PARAM15MIN.toByte())
            aapsLogger.debug(LTag.PUMPCOMM, "APS Temp basal start percent: $percent duration 15 min")
        }
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Set APS temp basal start result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set APS temp basal start result: $result")
        }
    }
}