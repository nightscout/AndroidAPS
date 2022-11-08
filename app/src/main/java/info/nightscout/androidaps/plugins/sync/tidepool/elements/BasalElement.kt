package info.nightscout.androidaps.plugins.sync.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class BasalElement(tbr: TemporaryBasal, private val profile: Profile, dateUtil: DateUtil)
    : BaseElement(tbr.timestamp, UUID.nameUUIDFromBytes(("AAPS-basal" + tbr.timestamp).toByteArray()).toString(), dateUtil) {

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