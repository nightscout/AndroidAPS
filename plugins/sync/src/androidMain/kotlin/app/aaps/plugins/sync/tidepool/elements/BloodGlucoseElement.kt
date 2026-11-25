package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.annotations.Expose
import java.util.LinkedList
import java.util.UUID
import kotlin.math.round

class BloodGlucoseElement(therapyEvent: TE, dateUtil: DateUtil, profileUtil: ProfileUtil) :
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
        // Rounded, half-even as AAPS shows it. toInt() cut the decimals off: 11.6 mmol/L is 208.98 mg/dL,
        // stored as 208, which Tidepool showed back as 11.5
        value = if (therapyEvent.glucose != null)
            round(profileUtil.convertToMgdl(therapyEvent.glucose!!, therapyEvent.glucoseUnit)).toInt()
        else 0
    }

    companion object {

        fun fromCareportalEvents(careportalList: List<TE>, dateUtil: DateUtil, profileUtil: ProfileUtil): List<BloodGlucoseElement> {
            val results = LinkedList<BloodGlucoseElement>()
            for (bt in careportalList) {
                if (bt.type == TE.Type.NS_MBG || bt.type == TE.Type.FINGER_STICK_BG_VALUE) {
                    val bge = BloodGlucoseElement(bt, dateUtil, profileUtil)
                    if (bge.value > 0)
                        results.add(BloodGlucoseElement(bt, dateUtil, profileUtil))
                }
            }
            return results
        }
    }
}