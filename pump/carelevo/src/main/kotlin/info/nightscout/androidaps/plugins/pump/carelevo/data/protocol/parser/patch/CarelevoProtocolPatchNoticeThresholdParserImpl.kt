package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolNoticeThresholdRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchNoticeThresholdParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolNoticeThresholdRspModel> {

    override fun parse(data: ByteArray): ProtocolNoticeThresholdRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val type = data[1].toUByte().toInt()
        val result = 0

        return ProtocolNoticeThresholdRspModel(
            timestamp,
            cmd,
            type = type,
            result = result
        )
    }
}