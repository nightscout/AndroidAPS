package info.nightscout.core.extensions

import info.nightscout.core.main.R
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject

fun GlucoseValue.valueToUnitsString(units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(value)
    else DecimalFormatter.to1Decimal(value * Constants.MGDL_TO_MMOLL)

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

fun InMemoryGlucoseValue.valueToUnitsString(units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(recalculated)
    else DecimalFormatter.to1Decimal(recalculated * Constants.MGDL_TO_MMOLL)

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