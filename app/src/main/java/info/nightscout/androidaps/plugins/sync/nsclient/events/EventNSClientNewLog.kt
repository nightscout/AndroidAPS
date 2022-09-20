package info.nightscout.androidaps.plugins.sync.nsclient.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.interfaces.NsClient
import java.text.SimpleDateFormat
import java.util.*

class EventNSClientNewLog(val action: String, val logText: String, val version: NsClient.Version) : Event() {
    var date = System.currentTimeMillis()

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