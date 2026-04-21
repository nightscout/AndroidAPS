package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionAckRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolCannulaInsertionAckParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolCannulaInsertionAckRspModel> {

    override fun parse(data: ByteArray): ProtocolCannulaInsertionAckRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolCannulaInsertionAckRspModel(
            timestamp,
            cmd,
            result
        )
    }
}