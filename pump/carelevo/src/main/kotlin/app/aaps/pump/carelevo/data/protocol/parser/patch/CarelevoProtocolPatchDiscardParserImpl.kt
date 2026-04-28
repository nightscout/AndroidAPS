package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchDiscardRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchDiscardParserImpl(
    override val command: Int
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