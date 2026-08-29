package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.annotations.Expose

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

    inner class Origin internal constructor(
        @field:Expose
        internal var id: String
    )
}