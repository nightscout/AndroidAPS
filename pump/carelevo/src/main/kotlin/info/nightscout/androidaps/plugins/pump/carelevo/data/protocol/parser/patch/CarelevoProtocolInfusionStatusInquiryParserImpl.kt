package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolInfusionStatusInquiryRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolInfusionStatusInquiryParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolInfusionStatusInquiryRptModel> {

    override fun parse(data: ByteArray): ProtocolInfusionStatusInquiryRptModel {
        val timeStamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val subId = data[1].toUByte().toInt()
        val runningHour = data[2].toUByte().toInt()
        val runningMin = data[3].toUByte().toInt()
        val runningTime = runningHour * 60 + runningMin
        val remainedInsulinVolumeUnit100 = data[4].toUByte().toInt() * 100
        val remainedInsulinVolumeInteger = data[5].toUByte().toInt()
        val remainedInsulinVolumeDecimal = data[6].toUByte().toInt() / 100.0
        val remainedInsulinVolume = remainedInsulinVolumeUnit100 + remainedInsulinVolumeInteger + remainedInsulinVolumeDecimal
        val infusedBasalVolumeInteger = data[7].toUByte().toInt()
        val infusedBasalVolumeDecimal = data[8].toUByte().toInt() / 100.0
        val infusedBasalVolume = infusedBasalVolumeInteger + infusedBasalVolumeDecimal
        val infusedBolusVolumeInteger = data[9].toUByte().toInt()
        val infusedBolusVolumeDecimal = data[10].toUByte().toInt() / 100.0
        val infusedBolusVolume = infusedBolusVolumeInteger + infusedBolusVolumeDecimal
        val pumpState = data[11].toUByte().toInt()
        val mode = data[12].toUByte().toInt()
        val infuseSetHour = data[13].toUByte().toInt()
        val infuseSetMin = data[14].toUByte().toInt()
        val infuseSetMins = infuseSetHour * 60 + infuseSetMin
        val currentInfusedProgramVolumeInteger = data[15].toUByte().toInt()
        val currentInfusedProgramVolumeDecimal = data[16].toUByte().toInt() / 100.0
        val currentInfusedProgramVolume = currentInfusedProgramVolumeInteger + currentInfusedProgramVolumeDecimal
        val realInfusedHour = data[17].toUByte().toInt()
        val realInfusedMin = data[18].toUByte().toInt()
        val realInfusedSec = data[19].toUByte().toInt()
        val realInfusedTime = (realInfusedHour * 60 +realInfusedMin) * 60 + realInfusedSec

        return ProtocolInfusionStatusInquiryRptModel(
            timeStamp,
            cmd,
            subId,
            runningTime,
            remainedInsulinVolume,
            infusedBasalVolume,
            infusedBolusVolume,
            pumpState,
            mode,
            infuseSetMins,
            currentInfusedProgramVolume,
            realInfusedTime
        )
    }
}