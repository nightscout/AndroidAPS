package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolExtendBolusInfusionParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolExtendBolusInfusionRspModel> {

    override fun parse(data: ByteArray): ProtocolExtendBolusInfusionRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()
        val expectedTime = (data[2].toUByte().toInt() * 60) + data[3].toUByte().toInt()

        return ProtocolExtendBolusInfusionRspModel(
            timestamp,
            cmd,
            result,
            expectedTime
        )
    }
}