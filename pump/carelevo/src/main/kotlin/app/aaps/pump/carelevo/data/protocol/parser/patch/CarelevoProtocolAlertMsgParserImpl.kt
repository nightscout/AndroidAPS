package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolAlertMsgRptModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolAlertMsgParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolAlertMsgRptModel> {

    override fun parse(data: ByteArray): ProtocolAlertMsgRptModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val cause = data[1].toUByte().toInt()
        val value = data[2].toUByte().toInt()

        return ProtocolAlertMsgRptModel(
            timestamp,
            cmd,
            cause,
            value
        )
    }
}