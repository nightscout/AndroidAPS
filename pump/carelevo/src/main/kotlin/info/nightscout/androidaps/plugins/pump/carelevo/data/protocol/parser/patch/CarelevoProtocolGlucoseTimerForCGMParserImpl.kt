package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolGlucoseTimerForCGMRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolGlucoseTimerForCGMParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolGlucoseTimerForCGMRspModel> {

    override fun parse(data: ByteArray): ProtocolGlucoseTimerForCGMRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[2].toUByte().toInt()
        val triggerType = data[1].toUByte().toInt()

        return ProtocolGlucoseTimerForCGMRspModel(
            timestamp,
            cmd,
            result,
            triggerType
        )
    }
}