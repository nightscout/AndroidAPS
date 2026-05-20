package app.aaps.pump.carelevo.data.protocol.parser.basal

import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalInfusionChangeRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolBasalInfusionChangeParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolBasalInfusionChangeRspModel> {

    override fun parse(data: ByteArray): ProtocolBasalInfusionChangeRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolBasalInfusionChangeRspModel(
            timestamp,
            cmd,
            result
        )
    }
}