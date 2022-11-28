package info.nightscout.rx.events

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.text.DateFormat

class EventNewHistoryData(val oldDataTimestamp: Long, var reloadBgData: Boolean, var newestGlucoseValueTimestamp: Long? = null) : Event() {

    override fun toString(): String {
        return super.toString() +
            " " +
            DateFormat.getDateInstance(DateFormat.SHORT).format(oldDataTimestamp) +
            " " +
            DateTime(oldDataTimestamp).toString(DateTimeFormat.forPattern("HH:mm:ss"))
    }
}