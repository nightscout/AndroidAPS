package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchExpiryExtendRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchExpiryExtendParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchExpiryExtendRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchExpiryExtendRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolPatchExpiryExtendRspModel(
            timestamp,
            cmd,
            result
        )
    }
}