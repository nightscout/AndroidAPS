package info.nightscout.plugins.sync.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.plugins.sync.nsclient.extensions.toMainUnit
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.utils.DateUtil
import java.util.LinkedList
import java.util.UUID

class BloodGlucoseElement(therapyEvent: TherapyEvent, dateUtil: DateUtil, profileUtil: ProfileUtil) :
    BaseElement(therapyEvent.timestamp, UUID.nameUUIDFromBytes(("AAPS-bg" + therapyEvent.timestamp).toByteArray()).toString(), dateUtil) {

    @Expose
    var subType: String = "manual"

    @Expose
    var units: String = "mg/dL"

    @Expose
    var value: Int = 0

    init {
        type = "smbg"
        subType = "manual"
        value = if (therapyEvent.glucose != null)
            profileUtil.convertToMgdl(therapyEvent.glucose!!, therapyEvent.glucoseUnit.toMainUnit()).toInt()
        else 0
    }

    companion object {

        fun fromCareportalEvents(careportalList: List<TherapyEvent>, dateUtil: DateUtil, profileUtil: ProfileUtil): List<BloodGlucoseElement> {
            val results = LinkedList<BloodGlucoseElement>()
            for (bt in careportalList) {
                if (bt.type == TherapyEvent.Type.NS_MBG || bt.type == TherapyEvent.Type.FINGER_STICK_BG_VALUE) {
                    val bge = BloodGlucoseElement(bt, dateUtil, profileUtil)
                    if (bge.value > 0)
                        results.add(BloodGlucoseElement(bt, dateUtil, profileUtil))
                }
            }
            return results
        }
    }
}