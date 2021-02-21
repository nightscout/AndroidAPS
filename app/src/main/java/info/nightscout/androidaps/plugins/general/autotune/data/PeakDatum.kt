package info.nightscout.androidaps.plugins.general.autotune.data

import org.json.JSONException
import org.json.JSONObject

class PeakDatum {

    var peak = 0.0
    var meanDeviation = 0.0
    var smrDeviation = 0.0
    var rmsDeviation = 0.0

    constructor() {}
    constructor(json: JSONObject) {
        try {
            if (json.has("peak")) peak = json.getDouble("peak")
            if (json.has("meanDeviation")) meanDeviation = json.getDouble("meanDeviation")
            if (json.has("SMRDeviation")) smrDeviation = json.getDouble("SMRDeviation")
            if (json.has("RMSDeviation")) rmsDeviation = json.getDouble("RMSDeviation")
        } catch (e: JSONException) {
        }
    }

    fun toJSON(): JSONObject {
        val crjson = JSONObject()
        try {
            crjson.put("peak", peak)
            crjson.put("meanDeviation", meanDeviation.toInt())
            crjson.put("SMRDeviation", smrDeviation)
            crjson.put("RMSDeviation", rmsDeviation.toInt())
        } catch (e: JSONException) {
        }
        return crjson
    }

    fun equals(obj: PeakDatum): Boolean {
        var isEqual = true
        if (peak != obj.peak) isEqual = false
        if (meanDeviation != obj.meanDeviation) isEqual = false
        if (smrDeviation != obj.smrDeviation) isEqual = false
        if (rmsDeviation != obj.rmsDeviation) isEqual = false
        return isEqual
    }
}