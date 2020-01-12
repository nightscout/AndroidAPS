package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin

class MsgCheckValue_k(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val danaRKoreanPlugin: DanaRKoreanPlugin

) : MessageBase() {

    init {
        SetCommand(0xF0F1)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.isNewPump = true
        aapsLogger.debug(LTag.PUMPCOMM, "New firmware confirmed")
        danaRPump.model = intFromBuff(bytes, 0, 1)
        danaRPump.protocol = intFromBuff(bytes, 1, 1)
        danaRPump.productCode = intFromBuff(bytes, 2, 1)
        if (danaRPump.model != DanaRPump.DOMESTIC_MODEL) {
            danaRKoreanPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected")
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaRPump.model))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaRPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaRPump.productCode))
    }
}