package app.aaps.plugins.aps.autotune.data

import app.aaps.plugins.aps.autotune.NonFiniteNumberException
import app.aaps.plugins.aps.autotune.finite
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DiaDeviation(var dia: Double = 0.0, var meanDeviation: Double = 0.0, var smrDeviation: Double = 0.0, var rmsDeviation: Double = 0.0) {

    constructor(json: JsonObject) : this() {
        try {
            if (json.containsKey("dia")) dia = json.double("dia")
            if (json.containsKey("meanDeviation")) meanDeviation = json.double("meanDeviation")
            if (json.containsKey("SMRDeviation")) smrDeviation = json.double("SMRDeviation")
            if (json.containsKey("RMSDeviation")) rmsDeviation = json.double("RMSDeviation")
        } catch (_: OrgJsonReadException) {
        }
    }

    fun toJSON(): JsonObject = buildJsonObject {
        try {
            put("dia", finite(dia))
            put("meanDeviation", meanDeviation.toInt())
            put("SMRDeviation", finite(smrDeviation))
            put("RMSDeviation", rmsDeviation.toInt())
        } catch (_: NonFiniteNumberException) {
        }
    }
}
