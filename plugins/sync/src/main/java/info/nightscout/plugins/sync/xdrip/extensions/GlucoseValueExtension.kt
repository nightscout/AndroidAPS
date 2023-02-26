package info.nightscout.plugins.sync.xdrip.extensions


import info.nightscout.database.entities.GlucoseValue
import org.json.JSONObject

fun GlucoseValue.toXdripJson(): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("mills", timestamp)
        .put("isValid", isValid)
        .put("mgdl", value)
        .put("direction", trendArrow.text)

