package app.aaps.core.objects.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Nightscout `entries` document for one reading.
 *
 * Write only - nothing here parses, so none of the lenient-read rules in [lenientInt] apply. Two
 * things still have to be written exactly as they are:
 *
 * - `_id` is added only when it exists. `org.json` DELETES a key when you put null into it, so the
 *   old code emitted no `_id` at all for a reading that has no Nightscout id. Writing
 *   `put("_id", ids.nightscoutId)` unguarded would instead emit `"_id":null`, which a reader sees as
 *   a present-but-null id.
 * - `timestamp` is put as a Long. Never convert it to Double first: that renders as `1.5147669E12`
 *   and destroys the date.
 *
 * Key order matches the old chained puts, because `glucoseToJSON` writes this into an
 * `entries<date>.json` file that users hand to oref0 autotune and read by eye.
 */
fun GV.toJsonObject(isAdd: Boolean, dateUtil: DateUtil): JsonObject =
    buildJsonObject {
        put("device", sourceSensor.text)
        put("date", timestamp)
        put("dateString", dateUtil.toISOString(timestamp))
        put("isValid", isValid)
        put("sgv", value)
        put("direction", trendArrow.text)
        put("type", "sgv")
        if (isAdd) ids.nightscoutId?.let { put("_id", it) }
    }

fun InMemoryGlucoseValue.valueToUnits(units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) recalculated
    else recalculated * Constants.MGDL_TO_MMOLL
