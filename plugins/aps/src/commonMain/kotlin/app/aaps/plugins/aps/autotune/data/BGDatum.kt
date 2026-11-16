package app.aaps.plugins.aps.autotune.data

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.autotune.NonFiniteNumberException
import app.aaps.plugins.aps.autotune.finite
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Created by Rumen Georgiev on 2/24/2018.
 */
class BGDatum {

    //Added by Rumen for autotune
    var id: Long = 0
    var date = 0L
    var value = 0.0
    var direction: TrendArrow? = null
    var deviation = 0.0
    var bgi = 0.0
    var mealAbsorption = ""
    var mealCarbs = 0
    var uamAbsorption = ""
    var avgDelta = 0.0
    var bgReading: GV? = null
        private set
    var dateUtil: DateUtil

    constructor(dateUtil: DateUtil) {
        this.dateUtil = dateUtil
    }

    /** Reads a value of an oref0 prep file. A value of the wrong type ends the reading, as `org.json` did. */
    constructor(json: JsonObject, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        try {
            //if (json.has("_id")) id = json.getLong("_id")
            if (json.containsKey("date")) date = json.long("date")
            if (json.containsKey("sgv")) value = json.double("sgv")
            if (json.containsKey("direction")) direction = TrendArrow.fromString(json.string("direction"))
            if (json.containsKey("deviation")) deviation = json.double("deviation")
            if (json.containsKey("BGI")) bgi = json.double("BGI")
            if (json.containsKey("avgDelta")) avgDelta = json.double("avgDelta")
            if (json.containsKey("mealAbsorption")) mealAbsorption = json.string("mealAbsorption")
            if (json.containsKey("mealCarbs")) mealCarbs = json.int("mealCarbs")
        } catch (_: OrgJsonReadException) {
        }
    }

    constructor(glucoseValue: GV, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        date = glucoseValue.timestamp
        value = glucoseValue.value
        direction = glucoseValue.trendArrow
        id = glucoseValue.id
        this.bgReading = glucoseValue
    }

    /** A value JSON cannot hold ends the object there, as the `JSONException` of `org.json` did. */
    fun toJSON(mealData: Boolean): JsonObject = buildJsonObject {
        val utcOffset = T.msecs(systemUtcOffsetAt(dateUtil.now())).hours()
        try {
            put("_id", id)
            put("date", date)
            put("dateString", dateUtil.toISOAsUTC(date))
            put("sgv", finite(value))
            direction?.let { put("direction", it.name) }
            put("type", "sgv")
            put("sysTime", dateUtil.toISOAsUTC(date))
            put("utcOffset", utcOffset)
            put("glucose", finite(value))
            put("avgDelta", finite(avgDelta))
            put("BGI", finite(bgi))
            put("deviation", finite(deviation))
            if (mealData) {
                put("mealAbsorption", mealAbsorption)
                put("mealCarbs", mealCarbs)
            }
        } catch (_: NonFiniteNumberException) {
        }
    }

    fun equals(obj: BGDatum): Boolean {
        var isEqual = true
        if (date / 1000 != obj.date / 1000) isEqual = false
        if (deviation != obj.deviation) isEqual = false
        if (avgDelta != obj.avgDelta) isEqual = false
        if (bgi != obj.bgi) isEqual = false
        if (mealAbsorption != obj.mealAbsorption) isEqual = false
        if (mealCarbs != obj.mealCarbs) isEqual = false
        return isEqual
    }
}
