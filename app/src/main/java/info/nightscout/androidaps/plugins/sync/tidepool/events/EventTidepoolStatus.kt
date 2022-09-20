package info.nightscout.androidaps.plugins.sync.tidepool.events

import info.nightscout.androidaps.events.Event
import java.text.SimpleDateFormat
import java.util.*

class EventTidepoolStatus(val status: String) : Event() {

    var date: Long = System.currentTimeMillis()

    private var timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun toPreparedHtml(): StringBuilder {
        val stringBuilder = StringBuilder()
        stringBuilder.append(timeFormat.format(date))
        stringBuilder.append(" <b>")
        stringBuilder.append(status)
        stringBuilder.append("</b> ")
        stringBuilder.append("<br>")
        return stringBuilder
    }
}