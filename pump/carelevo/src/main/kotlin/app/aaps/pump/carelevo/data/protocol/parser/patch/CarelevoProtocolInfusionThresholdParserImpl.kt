package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolInfusionThresholdRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolInfusionThresholdParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolInfusionThresholdRspModel> {

    override fun parse(data: ByteArray): ProtocolInfusionThresholdRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val type = data[1].toUByte().toInt()
        val result = data[2].toUByte().toInt()

        return ProtocolInfusionThresholdRspModel(
            timestamp,
            cmd,
            type,
            result
        )
    }
}