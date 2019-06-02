package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.plugins.treatments.Treatment

class BolusElement : BaseElement() {
    @Expose
    var subType = "normal"
    @Expose
    var normal: Double = 0.0
    @Expose
    var expectedNormal: Double = 0.0

    init {
        type = "bolus";
    }

    fun create(insulinDelivered: Double, timestamp: Long, uuid: String): BolusElement {
        this.normal = insulinDelivered
        this.expectedNormal = insulinDelivered
        populate(timestamp, uuid)
        return this
    }

    companion object {
        fun fromTreatment(treatment: Treatment): BolusElement {
            return BolusElement().create(treatment.insulin, treatment.date, "uuid-AAPS")
        }
    }
}