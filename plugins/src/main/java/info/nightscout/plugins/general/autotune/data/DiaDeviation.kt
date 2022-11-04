package info.nightscout.plugins.general.autotune.data

import org.json.JSONException
import org.json.JSONObject

class DiaDeviation(var dia: Double = 0.0, var meanDeviation: Double = 0.0, var smrDeviation: Double = 0.0, var rmsDeviation: Double = 0.0) {

    constructor(json: JSONObject) : this() {
        try {
            if (json.has("dia")) dia = json.getDouble("dia")
            if (json.has("meanDeviation")) meanDeviation = json.getDouble("meanDeviation")
            if (json.has("SMRDeviation")) smrDeviation = json.getDouble("SMRDeviation")
            if (json.has("RMSDeviation")) rmsDeviation = json.getDouble("RMSDeviation")
        } catch (e: JSONException) {
        }
    }

    fun toJSON(): JSONObject {
        val crjson = JSONObject()
        try {
            crjson.put("dia", dia)
            crjson.put("meanDeviation", meanDeviation.toInt())
            crjson.put("SMRDeviation", smrDeviation)
            crjson.put("RMSDeviation", rmsDeviation.toInt())
        } catch (e: JSONException) {
        }
        return crjson
    }
}