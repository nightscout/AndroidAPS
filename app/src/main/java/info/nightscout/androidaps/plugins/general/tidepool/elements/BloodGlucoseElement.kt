package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject
import java.util.*

class BloodGlucoseElement(careportalEvent: CareportalEvent)
    : BaseElement(careportalEvent.date, UUID.nameUUIDFromBytes(("AAPS-bg" + careportalEvent.date).toByteArray()).toString()) {

    @Expose
    var subType: String = "manual"
    @Expose
    var units: String = "mg/dL"
    @Expose
    var value: Int = 0

    init {
        type = "cbg"
        subType = "manual" // TODO
        val json = if (careportalEvent.json != null) JSONObject(careportalEvent.json) else JSONObject()
        value = Profile.toMgdl(JsonHelper.safeGetDouble(json, "glucose"), JsonHelper.safeGetString(json, "units", Constants.MGDL)).toInt()
    }

    companion object {

        fun fromCareportalEvents(careportalList: List<CareportalEvent>): List<BloodGlucoseElement> {
            val results = LinkedList<BloodGlucoseElement>()
            for (bt in careportalList) {
                if (bt.eventType == CareportalEvent.MBG || bt.eventType == CareportalEvent.BGCHECK) {
                    val bge = BloodGlucoseElement(bt)
                    if (bge.value > 0)
                        results.add(BloodGlucoseElement(bt))
                }
            }
            return results
        }
    }
}