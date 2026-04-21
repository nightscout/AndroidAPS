package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer

import org.joda.time.DateTime

class CarelevoDateTimeToByteTransformerImpl : CarelevoByteTransformer<String, ByteArray> {

    override fun transform(item: String): ByteArray {
        runCatching {
            val dateTime = DateTime()

            val year = dateTime.year.toString().substring(2).toInt()
            val month = dateTime.monthOfYear
            val day = dateTime.dayOfMonth
            val hour = dateTime.hourOfDay
            val min = dateTime.minuteOfHour
            val sec = dateTime.secondOfMinute

            return byteArrayOf(year.toByte(), month.toByte(), day.toByte(), hour.toByte(), min.toByte(), sec.toByte())
        }.getOrElse {
            throw IllegalArgumentException("$item cannot be parsed.")
        }
    }
}