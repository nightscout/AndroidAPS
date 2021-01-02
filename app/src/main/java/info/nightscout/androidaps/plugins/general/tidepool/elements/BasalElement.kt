package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.interfaces.ProfileFunction
import java.util.*

class BasalElement(tbr: TemporaryBasal, private val profileFunction: ProfileFunction)
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
        rate = tbr.tempBasalConvertedToAbsolute(tbr.date, profileFunction.getProfile(tbr.date))
        duration = tbr.end() - tbr.start()
    }
}