package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.db.BgReading
import java.util.*

class SensorGlucoseElement : BaseElement() {

    @Expose
    internal var units: String = "mg/dL"
    @Expose
    internal var value: Int = 0

    init {
        this.type = "cbg"
    }

    companion object {
        internal fun fromBgReading(bgReading: BgReading): SensorGlucoseElement {
            val sensorGlucose = SensorGlucoseElement()
            sensorGlucose.populate(bgReading.date, "uuid-AAPS")
            sensorGlucose.value = bgReading.value.toInt()
            return sensorGlucose
        }

        internal fun fromBgReadings(bgReadingList: List<BgReading>): List<SensorGlucoseElement> {
            val results = LinkedList<SensorGlucoseElement>()
            for (bgReading in bgReadingList) {
                results.add(fromBgReading(bgReading))
            }
            return results
        }
    }
}