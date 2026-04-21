package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolGlucoseTimerRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolGlucoseTimerParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolGlucoseTimerRptModel> {

    override fun parse(data: ByteArray): ProtocolGlucoseTimerRptModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()

        return ProtocolGlucoseTimerRptModel(
            timestamp,
            cmd
        )
    }
}