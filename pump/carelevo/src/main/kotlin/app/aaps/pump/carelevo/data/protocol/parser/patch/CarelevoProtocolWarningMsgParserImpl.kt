package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolWarningMsgRptModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolWarningMsgParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolWarningMsgRptModel> {

    override fun parse(data: ByteArray): ProtocolWarningMsgRptModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val cause = data[1].toUByte().toInt()
        val value = data[2].toUByte().toInt()

        return ProtocolWarningMsgRptModel(
            timestamp,
            cmd,
            cause,
            value
        )
    }
}