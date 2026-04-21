package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchInitRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchInitParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchInitRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchInitRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val mode = data[1].toUByte().toInt()

        return ProtocolPatchInitRspModel(
            timestamp,
            cmd,
            mode
        )
    }
}