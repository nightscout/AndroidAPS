package info.nightscout.plugins.general.autotune.data

import info.nightscout.shared.utils.DateUtil
import org.json.JSONException
import org.json.JSONObject

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

    constructor(json: JSONObject, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        try {
            if (json.has("CRInitialIOB")) crInitialIOB = json.getDouble("CRInitialIOB")
            if (json.has("CRInitialBG")) crInitialBG = json.getDouble("CRInitialBG")
            if (json.has("CRInitialCarbTime")) crInitialCarbTime = dateUtil.fromISODateString(json.getString("CRInitialCarbTime"))
            if (json.has("CREndIOB")) crEndIOB = json.getDouble("CREndIOB")
            if (json.has("CREndBG")) crEndBG = json.getDouble("CREndBG")
            if (json.has("CREndTime")) crEndTime = dateUtil.fromISODateString(json.getString("CREndTime"))
            if (json.has("CRCarbs")) crCarbs = json.getDouble("CRCarbs")
            if (json.has("CRInsulin")) crInsulin = json.getDouble("CRInsulin")
        } catch (e: JSONException) {
        }
    }

    fun toJSON(): JSONObject {
        val crjson = JSONObject()
        try {
            crjson.put("CRInitialIOB", crInitialIOB)
            crjson.put("CRInitialBG", crInitialBG.toInt())
            crjson.put("CRInitialCarbTime", dateUtil.toISOString(crInitialCarbTime))
            crjson.put("CREndIOB", crEndIOB)
            crjson.put("CREndBG", crEndBG.toInt())
            crjson.put("CREndTime", dateUtil.toISOString(crEndTime))
            crjson.put("CRCarbs", crCarbs.toInt())
            crjson.put("CRInsulin", crInsulin)
        } catch (e: JSONException) {
        }
        return crjson
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