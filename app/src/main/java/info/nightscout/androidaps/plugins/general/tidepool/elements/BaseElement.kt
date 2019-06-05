package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.utils.DateUtil

open class BaseElement(timestamp: Long, uuid: String) {
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
        deviceTime = DateUtil.toISONoZone(timestamp)
        time = DateUtil.toISOAsUTC(timestamp)
        timezoneOffset = DateUtil.getTimeZoneOffsetMinutes(timestamp) // TODO
        origin = Origin(uuid)
    }

    inner class Origin internal constructor(@field:Expose
                                            internal var id: String)
}