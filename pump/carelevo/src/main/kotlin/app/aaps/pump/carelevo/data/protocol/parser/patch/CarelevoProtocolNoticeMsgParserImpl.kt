package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolNoticeMsgRptModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolNoticeMsgParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolNoticeMsgRptModel> {

    override fun parse(data: ByteArray): ProtocolNoticeMsgRptModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val cause = data[1].toUByte().toInt()
        val value = data[2].toUByte().toInt()

        return ProtocolNoticeMsgRptModel(
            timestamp,
            cmd,
            cause,
            value
        )
    }
}