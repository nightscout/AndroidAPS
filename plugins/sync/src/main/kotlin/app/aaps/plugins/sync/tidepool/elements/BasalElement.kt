package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.convertedToAbsolute
import com.google.gson.annotations.Expose
import java.util.Calendar
import java.util.UUID

class BasalElement : BaseElement {

    internal var timestamp: Long = 0 // not exposed

    @Expose
    internal var deliveryType = "temp"

    @Expose
    internal var duration: Long = 0

    @Expose
    internal var rate = -1.0

    @Expose
    internal var scheduleName = "AAPS"

    @Expose
    internal var suppressed: SuppressedBasal? = null

    @Expose
    internal var clockDriftOffset: Long = 0

    @Expose
    internal var conversionOffset: Long = 0

    constructor(tbr: TB, profile: Profile, dateUtil: DateUtil) :
        this(tbr, tbr.timestamp, tbr.duration, profile, dateUtil)

    constructor(tbr: TB, timestamp: Long, duration: Long, profile: Profile, dateUtil: DateUtil) :
        super(timestamp, UUID.nameUUIDFromBytes(("AAPS-basal" + tbr.timestamp + "-" + timestamp).toByteArray()).toString(), dateUtil) {
        type = "basal"
        this.timestamp = timestamp
        rate = tbr.convertedToAbsolute(timestamp, profile)
        this.duration = duration
        val suppressedRate = profile.getBasalTimeFromMidnight(secondsFromMidnight(timestamp))
        suppressed = SuppressedBasal(suppressedRate)
    }

    constructor(timestamp: Long, duration: Long, rate: Double, dateUtil: DateUtil) :
        super(timestamp, UUID.nameUUIDFromBytes(("AAPS-basal-scheduled$timestamp").toByteArray()).toString(), dateUtil) {
        type = "basal"
        this.timestamp = timestamp
        deliveryType = "scheduled"
        this.duration = duration
        this.rate = rate
    }

    private fun secondsFromMidnight(timestamp: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.HOUR_OF_DAY) * 3600 + calendar.get(Calendar.MINUTE) * 60 + calendar.get(Calendar.SECOND)
    }

    class SuppressedBasal internal constructor(
        @field:Expose
        internal var rate: Double,
        @field:Expose
        internal var type: String = "basal",
        @field:Expose
        internal var deliveryType: String = "scheduled",
        @field:Expose
        internal var scheduleName: String = "AAPS"
    )
}
