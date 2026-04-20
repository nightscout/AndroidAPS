package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.basal

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolAdditionalBasalProgramSetRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolAdditionalBasalProgramSetParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolAdditionalBasalProgramSetRspModel> {

    override fun parse(data: ByteArray): ProtocolAdditionalBasalProgramSetRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolAdditionalBasalProgramSetRspModel(
            timestamp,
            cmd,
            result
        )
    }
}