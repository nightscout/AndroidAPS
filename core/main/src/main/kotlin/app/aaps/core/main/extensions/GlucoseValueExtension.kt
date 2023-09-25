package app.aaps.core.main.extensions

import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.R
import app.aaps.database.entities.GlucoseValue
import org.json.JSONObject

fun GlucoseValue.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("date", timestamp)
        .put("dateString", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("sgv", value)
        .put("direction", trendArrow.text)
        .put("type", "sgv")
        .also { if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId) }

fun InMemoryGlucoseValue.valueToUnits(units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) recalculated
    else recalculated * Constants.MGDL_TO_MMOLL

fun GlucoseValue.TrendArrow.directionToIcon(): Int =
    when (this) {
        GlucoseValue.TrendArrow.TRIPLE_DOWN     -> R.drawable.ic_invalid
        GlucoseValue.TrendArrow.DOUBLE_DOWN     -> R.drawable.ic_doubledown
        GlucoseValue.TrendArrow.SINGLE_DOWN     -> R.drawable.ic_singledown
        GlucoseValue.TrendArrow.FORTY_FIVE_DOWN -> R.drawable.ic_fortyfivedown
        GlucoseValue.TrendArrow.FLAT            -> R.drawable.ic_flat
        GlucoseValue.TrendArrow.FORTY_FIVE_UP   -> R.drawable.ic_fortyfiveup
        GlucoseValue.TrendArrow.SINGLE_UP       -> R.drawable.ic_singleup
        GlucoseValue.TrendArrow.DOUBLE_UP       -> R.drawable.ic_doubleup
        GlucoseValue.TrendArrow.TRIPLE_UP       -> R.drawable.ic_invalid
        GlucoseValue.TrendArrow.NONE            -> R.drawable.ic_invalid
    }