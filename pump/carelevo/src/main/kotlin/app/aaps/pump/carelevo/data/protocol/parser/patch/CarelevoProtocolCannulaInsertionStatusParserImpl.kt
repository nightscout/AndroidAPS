package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionStatusRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolCannulaInsertionStatusParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolCannulaInsertionStatusRspModel> {

    override fun parse(data: ByteArray): ProtocolCannulaInsertionStatusRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolCannulaInsertionStatusRspModel(
            timestamp,
            cmd,
            result
        )
    }
}