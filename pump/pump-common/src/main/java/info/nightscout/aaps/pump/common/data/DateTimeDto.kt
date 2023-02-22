package info.nightscout.aaps.pump.common.data

import info.nightscout.core.utils.DateTimeUtil
import org.joda.time.DateTime
import java.util.*

data class DateTimeDto constructor(var year: Int? = 0,
                                   var month: Int? = 0,
                                   var day: Int? = 0,
                                   var hour: Int? = 0,
                                   var minute: Int? = 0,
                                   var second: Int? = 0) {

    // var year: Int? = 0
    // var month: Int? = 0
    // var day: Int? = 0
    // var hour: Int? = 0
    // var minute: Int? = 0
    // var second: Int? = 0

    constructor(gc: GregorianCalendar): this(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH)+1,
                                         gc.get(Calendar.DAY_OF_MONTH),gc.get(Calendar.HOUR_OF_DAY),
                                         gc.get(Calendar.MINUTE), gc.get(Calendar.SECOND)) {

    }


    // constructor(var year: Int? = 0
    //             var month: Int? = 0
    //             var day: Int? = 0
    //             var hour: Int? = 0
    //             var minute: Int? = 0
    //             var second: Int? = 0) {
    //
    // }

    override fun toString(): String {
        return if (year != null && month != null && day != null && hour != null && minute != null && second != null) {
            "" + zeroPrefixed(day) + "." + zeroPrefixed(month) + "." + year + " " + zeroPrefixed(hour) + ":" + zeroPrefixed(minute) + ":" + zeroPrefixed(second)
        } else
            super.toString()
    }

    fun zeroPrefixed(num: Int?): String {
        return if (num!! < 10)
            "0" + num
        else
            "" + num
    }

    fun toATechDate(): Long {
        return DateTimeUtil.toATechDate(this.year!!, this.month!!, this.day!!, this.hour!!, this.minute!!, this.second!!)
    }

    fun toMillis(): Long {
        val calendar = GregorianCalendar(this.year!!, this.month!!-1, this.day!!, this.hour!!, this.minute!!, this.second!!)
        return calendar.timeInMillis
    }

    fun toLocalDateTime(): DateTime {
        return DateTime(this.year!!, this.month!!, this.day!!, this.hour!!, this.minute!!, this.second!!)
    }

}