package info.nightscout.plugins.sync.xdrip.extensions


import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject

fun GlucoseValue.toXdripJson(): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("mills", timestamp)
        .put("isValid", isValid)
        .put("mgdl", value)
        .put("direction", trendArrow.text)

