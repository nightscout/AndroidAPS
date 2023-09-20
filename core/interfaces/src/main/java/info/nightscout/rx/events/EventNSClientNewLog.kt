package info.nightscout.rx.events

import java.text.SimpleDateFormat
import java.util.Locale

class EventNSClientNewLog(val action: String, val logText: String?) : Event() {
    var date = System.currentTimeMillis()

    private var timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun toPreparedHtml(): StringBuilder {
        val stringBuilder = StringBuilder()
        stringBuilder.append(timeFormat.format(date))
        stringBuilder.append(" <b>")
        stringBuilder.append(action)
        stringBuilder.append("</b> ")
        stringBuilder.append(logText)
        return stringBuilder
    }
}