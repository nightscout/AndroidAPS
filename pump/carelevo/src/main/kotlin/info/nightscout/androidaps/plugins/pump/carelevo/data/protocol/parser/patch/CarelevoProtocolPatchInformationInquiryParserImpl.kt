package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchInformationInquiryRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolPatchInformationInquiryParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchInformationInquiryRptModel> {

    override fun parse(data: ByteArray): ProtocolPatchInformationInquiryRptModel {
        val timeStamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()
        // val productCL = data[2].toUByte().toInt().toChar()
        // val productTY = data[3].toUByte().toInt().toChar()
        // val productMO = data[4].toUByte().toInt().toChar()
        // val processCO = StringBuilder().append(data[5].toUByte().toInt().toChar())
        //     .append(data[6].toUByte().toInt().toChar()).toString()
        // val manufactureYE = StringBuilder().append(data[7].toUByte().toInt().toChar())
        //     .append(data[8].toUByte().toInt().toChar()).toString()
        // val manufactureMO = StringBuilder().append(data[9].toUByte().toInt().toChar()).toString()
        // val manufactureDA = StringBuilder().append(data[10].toUByte().toInt().toChar())
        //     .append(data[11].toUByte().toInt().toChar()).toString()
        // val manufactureLO = StringBuilder().append(data[12].toUByte().toInt().toChar()).toString()
        // val manufactureNO = StringBuilder().append(data[13].toUByte().toInt().toChar())
        //     .append(data[14].toUByte().toInt().toChar())
        //     .append(data[15].toUByte().toInt().toChar())
        //     .append(data[16].toUByte().toInt().toChar())
        //     .toString()

        val serialNum = StringBuilder()
            .append(data[2].toUByte().toInt().toChar())
            .append(data[3].toUByte().toInt().toChar())
            .append(data[4].toUByte().toInt().toChar())
            .append(data[5].toUByte().toInt().toChar())
            .append(data[6].toUByte().toInt().toChar())
            .append(data[7].toUByte().toInt().toChar())
            .append(data[8].toUByte().toInt().toChar())
            .append(data[9].toUByte().toInt().toChar())
            .append(data[10].toUByte().toInt().toChar())
            .append(data[11].toUByte().toInt().toChar())
            .append(data[12].toUByte().toInt().toChar())
            .append(data[13].toUByte().toInt().toChar())
            .append(data[14].toUByte().toInt().toChar())
            .toString()

        return ProtocolPatchInformationInquiryRptModel(
            timeStamp,
            cmd,
            result,
            // productCL.toString(),
            // productTY.toString(),
            // productMO.toString(),
            // processCO,
            // manufactureYE,
            // manufactureMO,
            // manufactureDA,
            // manufactureLO,
            // manufactureNO,
            serialNum
        )
    }
}