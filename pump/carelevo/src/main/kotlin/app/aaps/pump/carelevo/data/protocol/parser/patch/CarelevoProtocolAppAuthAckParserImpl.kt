package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthAckRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolAppAuthAckParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolAppAuthAckRspModel> {

    override fun parse(data: ByteArray): ProtocolAppAuthAckRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolAppAuthAckRspModel(
            timestamp,
            cmd,
            result
        )
    }
}