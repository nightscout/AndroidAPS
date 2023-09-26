package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.extensions.convertedToAbsolute
import app.aaps.database.entities.TemporaryBasal
import com.google.gson.annotations.Expose
import java.util.UUID

class BasalElement(tbr: TemporaryBasal, profile: Profile, dateUtil: DateUtil) : BaseElement(tbr.timestamp, UUID.nameUUIDFromBytes(("AAPS-basal" + tbr.timestamp).toByteArray()).toString(), dateUtil) {

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
        type = "basal"
        timestamp = tbr.timestamp
        rate = tbr.convertedToAbsolute(tbr.timestamp, profile)
        duration = tbr.duration
    }
}