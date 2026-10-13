package app.aaps.core.interfaces.rx.events

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fired when new history data is available from the pump.
 *
 * @param oldDataTimestamp The timestamp of the oldest data record in the new history.
 * @param reloadBgData If true, indicates that blood glucose data should also be reloaded.
 * @param newestGlucoseValueTimestamp The timestamp of the newest glucose value, if available.
 */
class EventNewHistoryData(val oldDataTimestamp: Long, var reloadBgData: Boolean, var newestGlucoseValueTimestamp: Long? = null) : Event() {

    override fun toString(): String {
        return super.toString() +
            " " +
            Instant.ofEpochMilli(oldDataTimestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
    }
}