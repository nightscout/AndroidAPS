package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolExtendBolusDelayRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolExtendBolusDelayRptParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolExtendBolusDelayRptModel> {

    override fun parse(data: ByteArray): ProtocolExtendBolusDelayRptModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val amountInteger = data[1].toUByte().toInt()
        val amountDecimal = data[2].toUByte().toInt() / 100.0
        val delayedAmount = amountInteger + amountDecimal
        val expectedMin = data[3].toUByte().toInt()
        val expectedSec = data[4].toUByte().toInt()
        val expectedTime = expectedMin * 60 + expectedSec

        return ProtocolExtendBolusDelayRptModel(
            timestamp,
            cmd,
            delayedAmount,
            expectedTime
        )
    }
}