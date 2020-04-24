package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgSettingShippingInfo(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x3207)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.serialNumber = stringFromBuff(bytes, 0, 10)
        danaRPump.shippingDate = dateFromBuff(bytes, 10)
        danaRPump.shippingCountry = asciiStringFromBuff(bytes, 13, 3)
        aapsLogger.debug(LTag.PUMPCOMM, "Serial number: " + danaRPump.serialNumber)
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping date: " + danaRPump.shippingDate)
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping country: " + danaRPump.shippingCountry)
    }
}