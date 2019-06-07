package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.plugins.treatments.Treatment
import java.util.*

class BolusElement(treatment: Treatment)
    : BaseElement(treatment.date, UUID.nameUUIDFromBytes(("AAPS-bolus" + treatment.date).toByteArray()).toString()) {

    @Expose
    var subType = "normal"
    @Expose
    var normal: Double = 0.0
    @Expose
    var expectedNormal: Double = 0.0

    init {
        type = "bolus"
        normal = treatment.insulin
        expectedNormal = treatment.insulin
    }
}