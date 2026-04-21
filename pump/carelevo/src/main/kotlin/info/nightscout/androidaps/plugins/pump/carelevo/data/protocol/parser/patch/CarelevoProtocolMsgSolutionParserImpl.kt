package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolMsgSolutionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolMsgSolutionParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolMsgSolutionRspModel> {

    override fun parse(data: ByteArray): ProtocolMsgSolutionRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val subId = data[1].toUByte().toInt()
        val cause = data[2].toUByte().toInt()
        val result = data[3].toUByte().toInt()

        return ProtocolMsgSolutionRspModel(
            timestamp = timestamp,
            command = cmd,
            subId = subId,
            cause = cause,
            result = result
        )
    }
}