package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchAlertAlarmSetRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchAlertAlarmSetParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchAlertAlarmSetRspModel> {

    override fun parse(data: ByteArray): ProtocolPatchAlertAlarmSetRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolPatchAlertAlarmSetRspModel(
            timestamp,
            cmd,
            result
        )
    }
}