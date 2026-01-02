package app.aaps.plugins.sync.xdrip.events

import app.aaps.core.interfaces.rx.events.Event
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fired when a new log message is received from xDrip.
 *
 * @param action The action that generated the log message.
 * @param logText The text of the log message.
 */
class EventXdripNewLog(val action: String, val logText: String?) : Event() {

    var date = System.currentTimeMillis()

    private var timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Converts the log message to an HTML string for display.
     *
     * @return A [StringBuilder] containing the HTML representation of the log message.
     */
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