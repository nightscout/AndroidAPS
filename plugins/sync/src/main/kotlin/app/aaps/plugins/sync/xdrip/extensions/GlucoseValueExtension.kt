package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.main.extensions.fromDb
import app.aaps.database.entities.GlucoseValue
import org.json.JSONObject

fun GlucoseValue.toXdripJson(): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("mills", timestamp)
        .put("isValid", isValid)
        .put("mgdl", value)
        .put("direction", trendArrow.fromDb().text)

