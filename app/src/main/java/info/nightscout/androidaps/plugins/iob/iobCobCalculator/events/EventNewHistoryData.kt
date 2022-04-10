package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.events.Event
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.text.DateFormat

class EventNewHistoryData(val oldDataTimestamp: Long, var reloadBgData: Boolean, var newestGlucoseValue : GlucoseValue? = null) : Event() {

    override fun toString(): String {
        return super.toString() +
            " " +
            DateFormat.getDateInstance(DateFormat.SHORT).format(oldDataTimestamp) +
            " " +
            DateTime(oldDataTimestamp).toString(DateTimeFormat.forPattern("HH:mm:ss"))
    }
}