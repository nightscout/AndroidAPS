package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun TemporaryTarget.lowValueToUnitsToString(units: String): String =
    if (units == Constants.MGDL) DecimalFormatter.to0Decimal(this.lowTarget)
    else DecimalFormatter.to1Decimal(this.lowTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.highValueToUnitsToString(units: String): String =
    if (units == Constants.MGDL) DecimalFormatter.to0Decimal(this.highTarget)
    else DecimalFormatter.to1Decimal(this.highTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.target(): Double =
    (this.lowTarget + this.highTarget) / 2

fun TemporaryTarget.friendlyDescription(units: String, resourceHelper: ResourceHelper): String =
    Profile.toTargetRangeString(lowTarget, highTarget, Constants.MGDL, units) +
        units +
        "@" + resourceHelper.gs(R.string.format_mins, TimeUnit.MILLISECONDS.toMinutes(duration)) + "(" + reason.text + ")"

/*
        create fake object with nsID and isValid == false
 */
fun temporaryTargetFromNsIdForInvalidating(nsId: String): TemporaryTarget =
    temporaryTargetFromJson(
        JSONObject()
            .put("mills", 1)
            .put("duration", -1)
            .put("reason", "fake")
            .put("_id", nsId)
            .put(NSUpload.ISVALID, false)
    )!!

fun temporaryTargetFromJson(jsonObject: JSONObject): TemporaryTarget? {
    val units = JsonHelper.safeGetString(jsonObject, "units", Constants.MGDL)
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val duration = JsonHelper.safeGetLongAllowNull(jsonObject, "duration", null) ?: return null
    var low = JsonHelper.safeGetDouble(jsonObject, "targetBottom")
    low = Profile.toMgdl(low, units)
    var high = JsonHelper.safeGetDouble(jsonObject, "targetTop")
    high = Profile.toMgdl(high, units)
    val reasonString = if (duration != 0L) JsonHelper.safeGetStringAllowNull(jsonObject, "reason", null)
        ?: return null else ""
    // this string can be localized from NS, it will not work in this case CUSTOM will be used
    val reason = TemporaryTarget.Reason.fromString(reasonString)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, NSUpload.ISVALID, true)

    if (duration > 0L) {
        // not ending event
        if (units == Constants.MMOL) {
            if (low < Constants.MIN_TT_MMOL) return null
            if (low > Constants.MAX_TT_MMOL) return null
            if (high < Constants.MIN_TT_MMOL) return null
            if (high > Constants.MAX_TT_MMOL) return null
            if (low > high) return null
        } else {
            if (low < Constants.MIN_TT_MGDL) return null
            if (low > Constants.MAX_TT_MGDL) return null
            if (high < Constants.MIN_TT_MGDL) return null
            if (high > Constants.MAX_TT_MGDL) return null
            if (low > high) return null
        }
    }
    val tt = TemporaryTarget(
        timestamp = timestamp,
        duration = TimeUnit.MINUTES.toMillis(duration),
        reason = reason,
        lowTarget = low,
        highTarget = high,
        isValid = isValid
    )
    tt.interfaceIDs.nightscoutId = id
    return tt
}