package info.nightscout.androidaps.plugins.sync.nsShared.events

import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.rx.events.Event
import java.text.SimpleDateFormat
import java.util.Locale

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