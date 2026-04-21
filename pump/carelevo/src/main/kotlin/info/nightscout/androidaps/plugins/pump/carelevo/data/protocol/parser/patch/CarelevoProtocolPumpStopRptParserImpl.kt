package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPumpStopRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPumpStopRptParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPumpStopRptModel> {

    override fun parse(data: ByteArray): ProtocolPumpStopRptModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val cause = data[1].toUByte().toInt()
        val mode = data[2].toUByte().toInt()
        val subId = data[3].toUByte().toInt()
        val infusedVolumeInteger = data[4].toUByte().toInt()
        val infusedVolumeDecimal = data[5].toUByte().toInt() / 100.0
        val infusedVolume = infusedVolumeInteger + infusedVolumeDecimal
        val unInfusedVolumeInteger = data[6].toUByte().toInt()
        val unInfusedVolumeDecimal = data[7].toUByte().toInt() / 100.0
        val unInfusedVolume = unInfusedVolumeInteger + unInfusedVolumeDecimal
        val temperature = data[8].toUByte().toInt()

        return ProtocolPumpStopRptModel(
            timestamp,
            cmd,
            0,
            cause,
            mode,
            subId,
            infusedVolume,
            unInfusedVolume,
            temperature
        )
    }
}