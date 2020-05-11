package info.nightscout.androidaps.danaRv2.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danar.comm.MessageBase

class MsgStatusAPS_v2(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : MessageBase() {

    init {
        SetCommand(0xE001)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val iob = intFromBuff(bytes, 0, 2) / 100.0
        val deliveredSoFar = intFromBuff(bytes, 2, 2) / 100.0
        danaPump.iob = iob
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered so far: $deliveredSoFar")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump IOB: $iob")
    }
}