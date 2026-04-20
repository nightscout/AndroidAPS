package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchBuzzInspectionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchBuzzInspectionParserImpl(
    override val command : Int
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