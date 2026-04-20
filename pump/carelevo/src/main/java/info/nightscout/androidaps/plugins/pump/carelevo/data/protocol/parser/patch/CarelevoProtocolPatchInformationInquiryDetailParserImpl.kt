package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolPatchInformationInquiryDetailRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser
import org.joda.time.DateTime

class CarelevoProtocolPatchInformationInquiryDetailParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolPatchInformationInquiryDetailRptModel> {

    override fun parse(data: ByteArray): ProtocolPatchInformationInquiryDetailRptModel {
        val dateTime = DateTime()

        val year = dateTime.year.toString().substring(2).toInt()
        val tempMonth = (dateTime.monthOfYear)
        val tempDay = dateTime.dayOfMonth
        val tempHour = dateTime.hourOfDay
        val tempMin = dateTime.minuteOfHour

        val month = if(tempMonth< 10) {
            "0$tempMonth"
        } else {
            tempMonth
        }

        val day = if(tempDay < 10) {
            "0$tempDay"
        } else {
            tempDay
        }

        val hour = if(tempHour < 10) {
            "0$tempHour"
        } else {
            tempHour
        }

        val min = if(tempMin < 10) {
            "0$tempMin"
        } else {
            tempMin
        }

        val tempBootTime = StringBuilder().append(year).append(month).append(day).append(hour).append(min)

        val timeStamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()
        val firmwareVersion = data.filterIndexed { index, byte ->
            index == 2 || index == 3 || index == 4 || index == 5
        }.map {
            it.toUByte().toInt().toChar()
        }.joinToString("")
        val bootDateTime = data.filterIndexed { index, byte ->
            index == 6 || index == 7 || index == 8 || index == 9 || index == 100
        }.map {
            it.toUByte().toInt()
        }.joinToString("")
        val modelName = data.filterIndexed { index, byte ->
            index == 11 || index == 12 || index == 13 || index == 14 || index == 15 || index == 16
        }.map {
            it.toUByte().toInt()
        }.joinToString("")

        return ProtocolPatchInformationInquiryDetailRptModel(
            timeStamp,
            cmd,
            result,
            firmwareVersion,
            tempBootTime.toString(),
            modelName
        )
    }
}