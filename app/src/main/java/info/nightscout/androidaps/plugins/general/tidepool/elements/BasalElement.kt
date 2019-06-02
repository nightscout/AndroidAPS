package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.utils.DateUtil

class BasalElement : BaseElement() {

    internal var timestamp: Long = 0 // not exposed

    @Expose
    internal var deliveryType = "automated"
    @Expose
    internal var duration: Long = 0
    @Expose
    internal var rate = -1.0
    @Expose
    internal var scheduleName = "AAPS"
    @Expose
    internal var clockDriftOffset: Long = 0
    @Expose
    internal var conversionOffset: Long = 0

    init {
        type = "basal";
    }

    fun create(rate: Double, timeStart: Long, duration: Long, uuid: String) : BasalElement {
        this.timestamp = timeStart
        this.rate = rate
        this.duration = duration
        populate(timeStart, uuid)
        return this
    }

    internal fun isValid(): Boolean {
        return rate > -1 && duration > 0
    }

    internal fun toS(): String {
        return rate.toString() + " Start: " + DateUtil.dateAndTimeFullString(timestamp) + " for: " + DateUtil.niceTimeScalar(duration)
    }
}