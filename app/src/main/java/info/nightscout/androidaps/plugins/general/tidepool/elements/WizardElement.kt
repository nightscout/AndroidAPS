package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.treatments.Treatment

class WizardElement internal constructor() : BaseElement() {

    @Expose
    var units = "mg/dL"
    @Expose
    var carbInput: Double = 0.toDouble()
    @Expose
    var insulinCarbRatio: Double = 0.toDouble()
    @Expose
    var bolus: BolusElement? = null

    init {
        type = "wizard"
    }

    companion object {

        fun fromTreatment(treatment: Treatment): WizardElement {
            val result = WizardElement().populate(treatment.date, "uuid-AAPS") as WizardElement
            result.carbInput = treatment.carbs
            result.insulinCarbRatio = ProfileFunctions.getInstance().getProfile(treatment.date)!!.ic
            if (treatment.insulin > 0) {
                result.bolus = BolusElement().create(treatment.insulin, treatment.date, "uuid-AAPS")
            } else {
                result.bolus = BolusElement().create(0.0001, treatment.date, "uuid-AAPS") // fake insulin record
            }
            return result
        }
    }

}
