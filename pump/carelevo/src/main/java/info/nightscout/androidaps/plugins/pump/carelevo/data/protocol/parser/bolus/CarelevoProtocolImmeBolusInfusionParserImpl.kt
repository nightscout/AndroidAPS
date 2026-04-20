package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolImmeBolusInfusionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolImmeBolusInfusionParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolImmeBolusInfusionRspModel> {

    override fun parse(data: ByteArray): ProtocolImmeBolusInfusionRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val actionId = data[1].toUByte().toInt()
        val result = data[2].toUByte().toInt()
        val expectedTime = (data[3].toUByte().toInt() * 60) + data[4].toUByte().toInt()
        val remains = (data[5].toUByte().toInt() * 100.0) + data[6].toUByte().toInt() + (data[7].toUByte().toInt() / 100.0)

        return ProtocolImmeBolusInfusionRspModel(
            timestamp,
            cmd,
            actionId,
            result,
            expectedTime,
            remains
        )
    }
}