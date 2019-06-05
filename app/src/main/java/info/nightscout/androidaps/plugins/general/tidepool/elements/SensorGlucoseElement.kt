package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.db.BgReading
import java.util.*

class SensorGlucoseElement(bgReading: BgReading)
    : BaseElement(bgReading.date, UUID.nameUUIDFromBytes(("AAPS-cgm" + bgReading.date).toByteArray()).toString()) {

    @Expose
    internal var units: String = "mg/dL"
    @Expose
    internal var value: Int = 0

    init {
        this.type = "cbg"
        value = bgReading.value.toInt()
    }

    companion object {
        internal fun fromBgReadings(bgReadingList: List<BgReading>): List<SensorGlucoseElement> {
            val results = LinkedList<SensorGlucoseElement>()
            for (bgReading in bgReadingList) {
                results.add(SensorGlucoseElement(bgReading))
            }
            return results
        }
    }
}