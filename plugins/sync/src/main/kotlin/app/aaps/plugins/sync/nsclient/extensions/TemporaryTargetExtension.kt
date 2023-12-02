package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun TT.Companion.fromJson(jsonObject: JSONObject, profileUtil: ProfileUtil): TT? {
    val units = GlucoseUnit.fromText(JsonHelper.safeGetString(jsonObject, "units", GlucoseUnit.MGDL.asText))
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val duration = JsonHelper.safeGetLongAllowNull(jsonObject, "duration", null) ?: return null
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    var low = JsonHelper.safeGetDouble(jsonObject, "targetBottom")
    low = profileUtil.convertToMgdl(low, units)
    var high = JsonHelper.safeGetDouble(jsonObject, "targetTop")
    high = profileUtil.convertToMgdl(high, units)
    val reasonString = if (duration != 0L) JsonHelper.safeGetStringAllowNull(jsonObject, "reason", null)
        ?: return null else ""
    // this string can be localized from NS, it will not work in this case CUSTOM will be used
    val reason = TT.Reason.fromString(reasonString)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)

    if (timestamp == 0L) return null

    if (duration > 0L) {
        // not ending event
        if (low < Constants.MIN_TT_MGDL) return null
        if (low > Constants.MAX_TT_MGDL) return null
        if (high < Constants.MIN_TT_MGDL) return null
        if (high > Constants.MAX_TT_MGDL) return null
        if (low > high) return null
    }
    val tt = TT(
        timestamp = timestamp,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        reason = reason,
        lowTarget = low,
        highTarget = high,
        isValid = isValid
    )
    tt.ids.nightscoutId = id
    return tt
}

fun TT.toJson(isAdd: Boolean, dateUtil: DateUtil, profileUtil: ProfileUtil): JSONObject =
    JSONObject()
        .put("eventType", TE.Type.TEMPORARY_TARGET.text)
        .put("duration", T.msecs(duration).mins())
        .put("durationInMilliseconds", duration)
        .put("isValid", isValid)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("timestamp", timestamp)
        .put("enteredBy", "AndroidAPS").also {
            if (lowTarget > 0) it
                .put("reason", reason.text)
                .put("targetBottom", profileUtil.fromMgdlToUnits(lowTarget))
                .put("targetTop", profileUtil.fromMgdlToUnits(highTarget))
                .put("units", profileUtil.units.asText)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }
