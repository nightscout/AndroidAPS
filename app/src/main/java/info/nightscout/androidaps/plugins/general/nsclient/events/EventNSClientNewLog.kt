package info.nightscout.androidaps.plugins.general.nsclient.events

import info.nightscout.androidaps.events.Event
import java.text.SimpleDateFormat
import java.util.*

class EventNSClientNewLog(var action: String, var logText: String) : Event() {
    var date = Date()

    private var timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun toPreparedHtml(): StringBuilder {
        val stringBuilder = StringBuilder()
        stringBuilder.append(timeFormat.format(date))
        stringBuilder.append(" <b>")
        stringBuilder.append(action)
        stringBuilder.append("</b> ")
        stringBuilder.append(logText)
        stringBuilder.append("<br>")
        return stringBuilder
    }
}
