package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class MsgSetTime(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    time: Date
) : MessageBase() {

    init {
        SetCommand(0x330a)
        AddParamDateTime(time)
        aapsLogger.debug(LTag.PUMPCOMM, "New message: time:" + dateUtil.dateAndTimeString(time))
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        failed = result != 1
        aapsLogger.debug(LTag.PUMPCOMM, "Result of setting time: $result")
    }
}