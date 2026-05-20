package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolBuzzUsageChangeRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolBuzzUsageChangeParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolBuzzUsageChangeRspModel> {

    override fun parse(data: ByteArray): ProtocolBuzzUsageChangeRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolBuzzUsageChangeRspModel(
            timestamp,
            cmd,
            result
        )
    }
}