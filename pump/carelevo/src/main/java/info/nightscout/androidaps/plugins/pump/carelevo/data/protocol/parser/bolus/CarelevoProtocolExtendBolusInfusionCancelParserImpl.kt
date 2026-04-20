package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionCancelRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolExtendBolusInfusionCancelParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolExtendBolusInfusionCancelRspModel> {

    override fun parse(data: ByteArray): ProtocolExtendBolusInfusionCancelRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()
        val infusedAmount = data[2].toUByte().toInt() + (data[3].toUByte().toInt() / 100.0)

        return ProtocolExtendBolusInfusionCancelRspModel(
            timestamp,
            cmd,
            result,
            infusedAmount
        )
    }
}