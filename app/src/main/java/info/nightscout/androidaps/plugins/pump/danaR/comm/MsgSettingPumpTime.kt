package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class MsgSettingPumpTime(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val dateUtil: DateUtil
) : MessageBase() {

    init {
        SetCommand(0x320A)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val time = Date(
            100 + intFromBuff(bytes, 5, 1),
            intFromBuff(bytes, 4, 1) - 1,
            intFromBuff(bytes, 3, 1),
            intFromBuff(bytes, 2, 1),
            intFromBuff(bytes, 1, 1),
            intFromBuff(bytes, 0, 1)
        ).time
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil.dateAndTimeString(time) + " Phone time: " + Date())
        danaRPump.pumpTime = time
    }

    override fun handleMessageNotReceived() {
        danaRPump.pumpTime = 0
    }
}