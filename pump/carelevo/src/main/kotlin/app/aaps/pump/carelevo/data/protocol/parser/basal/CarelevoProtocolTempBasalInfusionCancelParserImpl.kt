package app.aaps.pump.carelevo.data.protocol.parser.basal

import app.aaps.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionCancelRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolTempBasalInfusionCancelParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolTempBasalInfusionCancelRspModel> {

    override fun parse(data: ByteArray): ProtocolTempBasalInfusionCancelRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolTempBasalInfusionCancelRspModel(
            timestamp,
            cmd,
            result
        )
    }
}