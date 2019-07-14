package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.data.Intervals
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import java.util.*

class BasalElement(tbr: TemporaryBasal)
    : BaseElement(tbr.date, UUID.nameUUIDFromBytes(("AAPS-basal" + tbr.date).toByteArray()).toString()) {

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
        timestamp = tbr.date
        rate = tbr.tempBasalConvertedToAbsolute(tbr.date, ProfileFunctions.getInstance().getProfile(tbr.date))
        duration = tbr.end() - tbr.start()
    }

    companion object {
        internal fun fromTemporaryBasals(tbrList: Intervals<TemporaryBasal>, start: Long, end: Long): List<BasalElement> {
            val results = LinkedList<BasalElement>()
            for (tbr in tbrList.list) {
                if (tbr.date >= start && tbr.date <= end && tbr.durationInMinutes != 0)
                    results.add(BasalElement(tbr))
            }
            return results
        }
    }
}