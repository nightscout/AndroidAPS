package info.nightscout.androidaps.plugins.general.autotune.data

import info.nightscout.androidaps.database.entities.GlucoseValue.TrendArrow
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONException
import org.json.JSONObject
import java.util.*

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
    var bgReading: GlucoseValue? = null
        private set
    var dateUtil: DateUtil

    constructor(dateUtil: DateUtil) { this.dateUtil = dateUtil}
    constructor(json: JSONObject, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        //if (json.has("_id")) id = json.getLong("_id")
        if (json.has("date")) date = JsonHelper.safeGetLong(json,"date")
        if (json.has("sgv")) value = JsonHelper.safeGetDouble(json,"sgv")
        if (json.has("direction")) direction = TrendArrow.fromString(JsonHelper.safeGetString(json,"direction", "Flat"))
        if (json.has("deviation")) deviation = JsonHelper.safeGetDouble(json, "deviation")
        if (json.has("BGI")) bgi = JsonHelper.safeGetDouble(json, "BGI")
        if (json.has("avgDelta")) avgDelta = JsonHelper.safeGetDouble(json, "avgDelta")
        if (json.has("mealAbsorption")) mealAbsorption = JsonHelper.safeGetString(json,"mealAbsorption", "")
        if (json.has("mealCarbs")) mealCarbs = JsonHelper.safeGetInt(json,"mealCarbs")
    }

    constructor(glucoseValue: GlucoseValue, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        date = glucoseValue.timestamp
        value = glucoseValue.value
        direction = glucoseValue.trendArrow
        id = glucoseValue.id
        this.bgReading = glucoseValue
    }

    fun toJSON(mealData: Boolean): JSONObject {
        val bgjson = JSONObject()
        val utcOffset = T.msecs(TimeZone.getDefault().getOffset(dateUtil.now()).toLong()).hours()
        try {
            bgjson.put("_id", id)
            bgjson.put("date", date)
            bgjson.put("dateString", dateUtil.toISOAsUTC(date))
            bgjson.put("sgv", value)
            bgjson.put("direction", direction)
            bgjson.put("type", "sgv")
            bgjson.put("sysTime", dateUtil.toISOAsUTC(date))
            bgjson.put("utcOffset", utcOffset)
            bgjson.put("glucose", value)
            bgjson.put("avgDelta", avgDelta)
            bgjson.put("BGI", bgi)
            bgjson.put("deviation", deviation)
            if (mealData) {
                bgjson.put("mealAbsorption", mealAbsorption)
                bgjson.put("mealCarbs", mealCarbs)
            }
        } catch (e: JSONException) {
        }
        return bgjson
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