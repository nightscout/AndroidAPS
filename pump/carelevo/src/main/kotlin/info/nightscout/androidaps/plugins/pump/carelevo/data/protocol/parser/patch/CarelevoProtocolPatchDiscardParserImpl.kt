package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchDiscardRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchDiscardParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchDiscardRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchDiscardRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolPatchDiscardRspModel(
            timestamp,
            cmd,
            result
        )
    }
}