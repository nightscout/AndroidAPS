package app.aaps.pump.carelevo.data.protocol.parser.patch

import app.aaps.pump.carelevo.data.model.ble.ProtocolPumpStopRspModel
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPumpStopParserImpl(
    override val command: Int
) : CarelevoProtocolParser<ByteArray, ProtocolPumpStopRspModel> {

    override fun parse(data: ByteArray): ProtocolPumpStopRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()

        return ProtocolPumpStopRspModel(
            timestamp,
            cmd,
            result
        )
    }
}