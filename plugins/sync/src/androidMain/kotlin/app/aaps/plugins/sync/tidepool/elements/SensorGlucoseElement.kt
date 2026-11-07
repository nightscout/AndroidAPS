package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.annotations.Expose
import java.util.LinkedList
import java.util.UUID

class SensorGlucoseElement(bgReading: GV, dateUtil: DateUtil) :
    BaseElement(bgReading.timestamp, UUID.nameUUIDFromBytes(("AAPS-cgm" + bgReading.timestamp).toByteArray()).toString(), dateUtil) {

    @Expose
    internal var units: String = "mg/dL"

    @Expose
    internal var value: Int = 0

    init {
        this.type = "cbg"
        value = bgReading.value.toInt()
    }

    companion object {

        internal fun fromBgReadings(bgReadingList: List<GV>, dateUtil: DateUtil): List<SensorGlucoseElement> {
            val results = LinkedList<SensorGlucoseElement>()
            for (bgReading in bgReadingList) {
                results.add(SensorGlucoseElement(bgReading, dateUtil))
            }
            return results
        }
    }
}