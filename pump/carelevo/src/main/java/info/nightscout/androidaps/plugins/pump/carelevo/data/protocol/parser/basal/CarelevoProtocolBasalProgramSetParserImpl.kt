package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.basal

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolBasalProgramSetRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolBasalProgramSetParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolBasalProgramSetRspModel> {

    override fun parse(data: ByteArray): ProtocolBasalProgramSetRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolBasalProgramSetRspModel(
            timestamp,
            cmd,
            result
        )
    }
}