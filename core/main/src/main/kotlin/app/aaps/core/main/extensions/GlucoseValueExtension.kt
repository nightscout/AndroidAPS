package app.aaps.core.main.extensions

import app.aaps.core.interfaces.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.R
import app.aaps.data.configuration.Constants
import app.aaps.data.db.GlucoseUnit
import app.aaps.data.db.TrendArrow
import app.aaps.database.entities.GlucoseValue
import org.json.JSONObject

fun GlucoseValue.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("date", timestamp)
        .put("dateString", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("sgv", value)
        .put("direction", trendArrow.fromDb().text)
        .put("type", "sgv")
        .also { if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId) }

fun InMemoryGlucoseValue.valueToUnits(units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) recalculated
    else recalculated * Constants.MGDL_TO_MMOLL

fun TrendArrow.directionToIcon(): Int =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> R.drawable.ic_invalid
        TrendArrow.DOUBLE_DOWN     -> R.drawable.ic_doubledown
        TrendArrow.SINGLE_DOWN     -> R.drawable.ic_singledown
        TrendArrow.FORTY_FIVE_DOWN -> R.drawable.ic_fortyfivedown
        TrendArrow.FLAT            -> R.drawable.ic_flat
        TrendArrow.FORTY_FIVE_UP   -> R.drawable.ic_fortyfiveup
        TrendArrow.SINGLE_UP       -> R.drawable.ic_singleup
        TrendArrow.DOUBLE_UP       -> R.drawable.ic_doubleup
        TrendArrow.TRIPLE_UP       -> R.drawable.ic_invalid
        TrendArrow.NONE            -> R.drawable.ic_invalid
    }

fun GlucoseValue.TrendArrow.fromDb(): TrendArrow =
    when (this) {
        GlucoseValue.TrendArrow.TRIPLE_DOWN     -> TrendArrow.TRIPLE_DOWN
        GlucoseValue.TrendArrow.DOUBLE_DOWN     -> TrendArrow.DOUBLE_DOWN
        GlucoseValue.TrendArrow.SINGLE_DOWN     -> TrendArrow.SINGLE_DOWN
        GlucoseValue.TrendArrow.FORTY_FIVE_DOWN -> TrendArrow.FORTY_FIVE_DOWN
        GlucoseValue.TrendArrow.FLAT            -> TrendArrow.FLAT
        GlucoseValue.TrendArrow.FORTY_FIVE_UP   -> TrendArrow.FORTY_FIVE_UP
        GlucoseValue.TrendArrow.SINGLE_UP       -> TrendArrow.SINGLE_UP
        GlucoseValue.TrendArrow.DOUBLE_UP       -> TrendArrow.DOUBLE_UP
        GlucoseValue.TrendArrow.TRIPLE_UP       -> TrendArrow.TRIPLE_UP
        GlucoseValue.TrendArrow.NONE            -> TrendArrow.NONE
    }
fun TrendArrow.toDb(): GlucoseValue.TrendArrow =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> GlucoseValue.TrendArrow.TRIPLE_DOWN
        TrendArrow.DOUBLE_DOWN     -> GlucoseValue.TrendArrow.DOUBLE_DOWN
        TrendArrow.SINGLE_DOWN     -> GlucoseValue.TrendArrow.SINGLE_DOWN
        TrendArrow.FORTY_FIVE_DOWN -> GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
        TrendArrow.FLAT            -> GlucoseValue.TrendArrow.FLAT
        TrendArrow.FORTY_FIVE_UP   -> GlucoseValue.TrendArrow.FORTY_FIVE_UP
        TrendArrow.SINGLE_UP       -> GlucoseValue.TrendArrow.SINGLE_UP
        TrendArrow.DOUBLE_UP       -> GlucoseValue.TrendArrow.DOUBLE_UP
        TrendArrow.TRIPLE_UP       -> GlucoseValue.TrendArrow.TRIPLE_UP
        TrendArrow.NONE            -> GlucoseValue.TrendArrow.NONE
    }