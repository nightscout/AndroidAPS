package info.nightscout.androidaps.danaRKorean.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class MsgCheckValue_k(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump,
    private val danaRKoreanPlugin: DanaRKoreanPlugin

) : MessageBase() {

    init {
        SetCommand(0xF0F1)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.isNewPump = true
        aapsLogger.debug(LTag.PUMPCOMM, "New firmware confirmed")
        danaPump.hwModel = intFromBuff(bytes, 0, 1)
        danaPump.protocol = intFromBuff(bytes, 1, 1)
        danaPump.productCode = intFromBuff(bytes, 2, 1)
        if (danaPump.hwModel != info.nightscout.androidaps.dana.DanaPump.DOMESTIC_MODEL) {
            danaRKoreanPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected")
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaPump.hwModel))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaPump.productCode))
    }
}