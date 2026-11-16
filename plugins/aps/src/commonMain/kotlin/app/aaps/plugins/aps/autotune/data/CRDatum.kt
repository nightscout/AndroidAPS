package app.aaps.plugins.aps.autotune.data

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.autotune.NonFiniteNumberException
import app.aaps.plugins.aps.autotune.finite
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Created by Rumen Georgiev on 2/26/2018.
 */
class CRDatum {

    var crInitialIOB = 0.0
    var crInitialBG = 0.0
    var crInitialCarbTime = 0L
    var crEndIOB = 0.0
    var crEndBG = 0.0
    var crEndTime = 0L
    var crCarbs = 0.0
    var crInsulin = 0.0
    var crInsulinTotal = 0.0
    var dateUtil: DateUtil

    constructor(dateUtil: DateUtil) {
        this.dateUtil = dateUtil
    }

    /** Reads a value of an oref0 prep file. A value of the wrong type ends the reading, as `org.json` did. */
    constructor(json: JsonObject, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        try {
            if (json.containsKey("CRInitialIOB")) crInitialIOB = json.double("CRInitialIOB")
            if (json.containsKey("CRInitialBG")) crInitialBG = json.double("CRInitialBG")
            if (json.containsKey("CRInitialCarbTime")) crInitialCarbTime = dateUtil.fromISODateString(json.string("CRInitialCarbTime"))
            if (json.containsKey("CREndIOB")) crEndIOB = json.double("CREndIOB")
            if (json.containsKey("CREndBG")) crEndBG = json.double("CREndBG")
            if (json.containsKey("CREndTime")) crEndTime = dateUtil.fromISODateString(json.string("CREndTime"))
            if (json.containsKey("CRCarbs")) crCarbs = json.double("CRCarbs")
            if (json.containsKey("CRInsulin")) crInsulin = json.double("CRInsulin")
        } catch (_: OrgJsonReadException) {
        }
    }

    /** A value JSON cannot hold ends the object there, as the `JSONException` of `org.json` did. */
    fun toJSON(): JsonObject = buildJsonObject {
        try {
            put("CRInitialIOB", finite(crInitialIOB))
            put("CRInitialBG", crInitialBG.toInt())
            put("CRInitialCarbTime", dateUtil.toISOString(crInitialCarbTime))
            put("CREndIOB", finite(crEndIOB))
            put("CREndBG", crEndBG.toInt())
            put("CREndTime", dateUtil.toISOString(crEndTime))
            put("CRCarbs", crCarbs.toInt())
            put("CRInsulin", finite(crInsulin))
        } catch (_: NonFiniteNumberException) {
        }
    }

    fun equals(obj: CRDatum): Boolean {
        var isEqual = true
        if (crInitialIOB != obj.crInitialIOB) isEqual = false
        if (crInitialBG != obj.crInitialBG) isEqual = false
        if (crInitialCarbTime / 1000 != obj.crInitialCarbTime / 1000) isEqual = false
        if (crEndIOB != obj.crEndIOB) isEqual = false
        if (crEndBG != obj.crEndBG) isEqual = false
        if (crEndTime / 1000 != obj.crEndTime / 1000) isEqual = false
        if (crCarbs != obj.crCarbs) isEqual = false
        if (crInsulin != obj.crInsulin) isEqual = false
        return isEqual
    }
}
