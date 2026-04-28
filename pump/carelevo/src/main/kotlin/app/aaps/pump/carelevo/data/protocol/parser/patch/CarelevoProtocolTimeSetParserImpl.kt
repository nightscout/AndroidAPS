package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolSetTimeRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolTimeSetParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolSetTimeRspModel> {

    override fun parse(data: ByteArray): ProtocolSetTimeRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolSetTimeRspModel(
            timestamp,
            cmd,
            result
        )
    }
}