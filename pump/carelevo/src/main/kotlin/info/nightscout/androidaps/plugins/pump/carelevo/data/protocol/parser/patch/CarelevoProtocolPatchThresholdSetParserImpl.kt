package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchThresholdSetRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchThresholdSetParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchThresholdSetRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchThresholdSetRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolPatchThresholdSetRspModel(
            timestamp,
            cmd,
            result
        )
    }
}