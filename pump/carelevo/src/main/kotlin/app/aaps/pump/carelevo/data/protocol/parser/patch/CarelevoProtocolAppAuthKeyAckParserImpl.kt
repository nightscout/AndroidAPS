package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthKeyAckRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolAppAuthKeyAckParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolAppAuthKeyAckRspModel> {

    override fun parse(data: ByteArray): ProtocolAppAuthKeyAckRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val value = data[1].toUByte().toInt()

        return ProtocolAppAuthKeyAckRspModel(
            timestamp,
            cmd,
            value
        )
    }
}