package app.aaps.core.interfaces.rx.events

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventNewHistoryData(val oldDataTimestamp: Long, var reloadBgData: Boolean, var newestGlucoseValueTimestamp: Long? = null) : Event() {

    override fun toString(): String {
        return super.toString() +
            " " +
            Instant.ofEpochMilli(oldDataTimestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
    }
}