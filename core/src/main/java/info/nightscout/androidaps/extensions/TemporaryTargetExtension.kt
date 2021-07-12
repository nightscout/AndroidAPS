package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun TemporaryTarget.isInProgress(dateUtil: DateUtil): Boolean =
    dateUtil.now() in timestamp..timestamp + duration

fun TemporaryTarget.lowValueToUnitsToString(units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(this.lowTarget)
    else DecimalFormatter.to1Decimal(this.lowTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.highValueToUnitsToString(units: GlucoseUnit): String =
    if (units == GlucoseUnit.MGDL) DecimalFormatter.to0Decimal(this.highTarget)
    else DecimalFormatter.to1Decimal(this.highTarget * Constants.MGDL_TO_MMOLL)

fun TemporaryTarget.target(): Double =
    (this.lowTarget + this.highTarget) / 2

fun TemporaryTarget.friendlyDescription(units: GlucoseUnit, resourceHelper: ResourceHelper): String =
    Profile.toTargetRangeString(lowTarget, highTarget, GlucoseUnit.MGDL, units) +
        units.asText +
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
            .put("isValid", false)
    )!!

fun temporaryTargetFromJson(jsonObject: JSONObject): TemporaryTarget? {
    val units = GlucoseUnit.fromText(JsonHelper.safeGetString(jsonObject, "units", Constants.MGDL))
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
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)

    if (timestamp == 0L) return null

    if (duration > 0L) {
        // not ending event
        if (units == GlucoseUnit.MMOL) {
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

fun TemporaryTarget.toJson(isAdd: Boolean, units: GlucoseUnit, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", TherapyEvent.Type.TEMPORARY_TARGET.text)
        .put("duration", T.msecs(duration).mins())
        .put("isValid", isValid)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "AndroidAPS").also {
            if (lowTarget > 0) it
                .put("reason", reason.text)
                .put("targetBottom", Profile.fromMgdlToUnits(lowTarget, units))
                .put("targetTop", Profile.fromMgdlToUnits(highTarget, units))
                .put("units", units.asText)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }
