package app.aaps.core.objects.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.R
import org.json.JSONObject

fun GV.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("date", timestamp)
        .put("dateString", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("sgv", value)
        .put("direction", trendArrow.text)
        .put("type", "sgv")
        .also { if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId) }

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
