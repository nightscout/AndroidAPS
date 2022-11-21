package info.nightscout.plugins.sync.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.shared.utils.DateUtil

open class BaseElement(timestamp: Long, uuid: String, dateUtil: DateUtil) {
    @Expose
    var deviceTime: String = ""
    @Expose
    var time: String = ""
    @Expose
    var timezoneOffset: Int = 0
    @Expose
    var type: String? = null
    @Expose
    var origin: Origin? = null

    init {
        deviceTime = dateUtil.toISONoZone(timestamp)
        time = dateUtil.toISOAsUTC(timestamp)
        timezoneOffset = dateUtil.getTimeZoneOffsetMinutes(timestamp) // TODO
        origin = Origin(uuid)
    }

    inner class Origin internal constructor(@field:Expose
                                            internal var id: String)
}