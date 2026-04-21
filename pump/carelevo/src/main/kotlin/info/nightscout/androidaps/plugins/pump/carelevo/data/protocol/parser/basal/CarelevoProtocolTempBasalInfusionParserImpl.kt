package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.basal

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolTempBasalInfusionParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolTempBasalInfusionRspModel> {

    override fun parse(data: ByteArray): ProtocolTempBasalInfusionRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolTempBasalInfusionRspModel(
            timestamp,
            cmd,
            result
        )
    }
}