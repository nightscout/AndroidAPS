package info.nightscout.androidaps.plugins.sync.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.toMainUnit
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class BloodGlucoseElement(therapyEvent: TherapyEvent, dateUtil: DateUtil)
    : BaseElement(therapyEvent.timestamp, UUID.nameUUIDFromBytes(("AAPS-bg" + therapyEvent.timestamp).toByteArray()).toString(), dateUtil) {

    @Expose
    var subType: String = "manual"

    @Expose
    var units: String = "mg/dL"

    @Expose
    var value: Int = 0

    init {
        type = "cbg"
        subType = "manual" // TODO
        value = if (therapyEvent.glucose != null)
            Profile.toMgdl(therapyEvent.glucose!!, therapyEvent.glucoseUnit.toMainUnit()).toInt()
        else 0
    }

    companion object {

        fun fromCareportalEvents(careportalList: List<TherapyEvent>, dateUtil: DateUtil): List<BloodGlucoseElement> {
            val results = LinkedList<BloodGlucoseElement>()
            for (bt in careportalList) {
                if (bt.type == TherapyEvent.Type.NS_MBG || bt.type == TherapyEvent.Type.FINGER_STICK_BG_VALUE) {
                    val bge = BloodGlucoseElement(bt, dateUtil)
                    if (bge.value > 0)
                        results.add(BloodGlucoseElement(bt, dateUtil))
                }
            }
            return results
        }
    }
}