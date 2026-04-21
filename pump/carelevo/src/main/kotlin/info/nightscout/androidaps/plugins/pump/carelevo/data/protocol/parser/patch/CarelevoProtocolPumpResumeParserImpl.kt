package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPumpResumeRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPumpResumeParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPumpResumeRspModel> {

    override fun parse(data: ByteArray): ProtocolPumpResumeRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()
        val mode = data[2].toUByte().toInt()
        val subId = if(data.size > 3) {
            data[3].toUByte().toInt()
        } else {
            0
        }

        return ProtocolPumpResumeRspModel(
            timestamp,
            cmd,
            result,
            mode,
            subId
        )
    }
}