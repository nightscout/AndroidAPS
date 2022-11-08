package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject

fun useDataSmoothing(sp: SP): Boolean {
    return sp.getBoolean(R.string.key_use_data_smoothing, false)
}

fun GlucoseValue.rawOrSmoothed(sp: SP): Double {
    if (useDataSmoothing(sp)) return smoothed ?: value
    else return value
}

fun GlucoseValue.rawOrSmoothed(useSmoothed: Boolean): Double {
    if (useSmoothed) return smoothed ?: value
    else return value
}

fun GlucoseValue.valueToUnits(units: GlucoseUnit, sp: SP): Double =
    if (units == GlucoseUnit.MGDL) rawOrSmoothed(sp)
    else rawOrSmoothed(sp) * Constants.MGDL_TO_MMOLL

fun GlucoseValue.valueToUnitsString(units: GlucoseUnit, sp: SP): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(rawOrSmoothed(sp))
    else DecimalFormatter.to1Decimal(rawOrSmoothed(sp) * Constants.MGDL_TO_MMOLL)

fun GlucoseValue.toJson(isAdd : Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("device", sourceSensor.text)
        .put("date", timestamp)
        .put("dateString", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("sgv", value)
        .put("direction", trendArrow.text)
        .put("type", "sgv")
        .also { if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId) }
