package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.GV
import org.json.JSONObject

fun GV.toXdripJson(): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("mills", timestamp)
        .put("isValid", isValid)
        .put("mgdl", value)
        .put("direction", trendArrow.text)

