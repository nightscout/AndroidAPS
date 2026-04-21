package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolAdditionalPrimingRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolAdditionalPrimingParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolAdditionalPrimingRspModel> {

    override fun parse(data: ByteArray): ProtocolAdditionalPrimingRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolAdditionalPrimingRspModel(
            timestamp,
            cmd,
            result
        )
    }
}