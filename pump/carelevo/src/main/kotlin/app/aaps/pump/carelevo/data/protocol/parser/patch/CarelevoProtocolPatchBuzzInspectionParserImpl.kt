package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchBuzzInspectionRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchBuzzInspectionParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchBuzzInspectionRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchBuzzInspectionRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolPatchBuzzInspectionRspModel(
            timestamp,
            cmd,
            result
        )
    }
}