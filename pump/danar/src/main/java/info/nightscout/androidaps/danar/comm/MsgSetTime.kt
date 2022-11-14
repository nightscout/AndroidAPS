package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgSetTime(
    injector: HasAndroidInjector,
    time: Long
) : MessageBase(injector) {

    init {
        setCommand(0x330a)
        addParamDateTimeReversed(time)
        aapsLogger.debug(LTag.PUMPCOMM, "New message: time:" + dateUtil.dateAndTimeString(time))
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        failed = result != 1
        aapsLogger.debug(LTag.PUMPCOMM, "Result of setting time: $result")
    }
}