package app.aaps.pump.carelevo.data.protocol.parser.basal

import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalBasalInfusionChangeRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolAdditionalBasalInfusionChangeParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolAdditionalBasalInfusionChangeRspModel> {

    override fun parse(data: ByteArray): ProtocolAdditionalBasalInfusionChangeRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolAdditionalBasalInfusionChangeRspModel(
            timestamp,
            cmd,
            result
        )
    }
}