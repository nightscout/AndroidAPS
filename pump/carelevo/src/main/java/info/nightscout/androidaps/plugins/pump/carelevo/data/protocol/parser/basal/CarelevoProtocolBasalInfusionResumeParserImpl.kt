package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.basal

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolBasalInfusionResumeRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolBasalInfusionResumeParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolBasalInfusionResumeRspModel> {

    override fun parse(data: ByteArray): ProtocolBasalInfusionResumeRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val segmentNo = data[1].toUByte().toInt()
        val infusionSpeed = data[2].toUByte().toInt() + (data[3].toUByte().toInt() / 100.0)
        val infusionPeriod = (data[4].toUByte().toInt() * 60) + data[5].toUByte().toInt()
        val insulinRemains = (data[6].toUByte().toInt() * 100) + data[7].toUByte().toInt() + (data[8].toUByte().toInt() / 100.0)

        return ProtocolBasalInfusionResumeRspModel(
            timestamp,
            cmd,
            segmentNo,
            infusionSpeed,
            infusionPeriod,
            insulinRemains
        )
    }
}