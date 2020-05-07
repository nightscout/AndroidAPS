package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.DateUtil

class DanaRS_Packet_General_Get_Shipping_Information(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val dateUtil: DateUtil
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 18) {
            failed = true
            return
        } else failed = false
        var dataIndex = DATA_START
        var dataSize = 10
        danaRPump.serialNumber = stringFromBuff(data, dataIndex, dataSize)
        dataIndex += dataSize
        dataSize = 3
        danaRPump.shippingDate = dateFromBuff(data, dataIndex)
        dataIndex += dataSize
        dataSize = 3
        danaRPump.shippingCountry = asciiStringFromBuff(data, dataIndex, dataSize)
        aapsLogger.debug(LTag.PUMPCOMM, "Serial number: " + danaRPump.serialNumber)
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping date: " + dateUtil.dateAndTimeString(danaRPump.shippingDate))
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping country: " + danaRPump.shippingCountry)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_SHIPPING_INFORMATION"
    }
}