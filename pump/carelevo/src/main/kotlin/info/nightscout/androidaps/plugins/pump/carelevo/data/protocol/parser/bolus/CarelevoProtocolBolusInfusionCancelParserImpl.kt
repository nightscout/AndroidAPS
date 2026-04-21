package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolBolusInfusionCancelRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolBolusInfusionCancelParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolBolusInfusionCancelRspModel> {

    override fun parse(data: ByteArray): ProtocolBolusInfusionCancelRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()
        val infusedAmount = data[2].toUByte().toInt() + (data[3].toUByte().toInt() / 100.0)

        return ProtocolBolusInfusionCancelRspModel(
            timestamp,
            cmd,
            result,
            0.0,
            infusedAmount
        )
    }
}