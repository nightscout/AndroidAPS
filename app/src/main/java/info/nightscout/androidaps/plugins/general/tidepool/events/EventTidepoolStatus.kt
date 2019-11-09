package info.nightscout.androidaps.plugins.general.tidepool.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.LocaleHelper
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat

class EventTidepoolStatus(val status: String) : Event() {
    private val log = LoggerFactory.getLogger(L.TIDEPOOL)

    var date: Long = DateUtil.now()

    init {
        if (L.isEnabled(L.TIDEPOOL))
            log.debug("New status: $status")
    }

    private var timeFormat = SimpleDateFormat("HH:mm:ss", LocaleHelper.currentLocale())

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