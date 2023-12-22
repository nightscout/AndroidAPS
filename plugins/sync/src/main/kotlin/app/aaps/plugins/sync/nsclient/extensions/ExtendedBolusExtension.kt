package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.toTemporaryBasal
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun EB.toJson(isAdd: Boolean, profile: Profile?, dateUtil: DateUtil): JSONObject? =
    profile?.let {
        if (isEmulatingTempBasal)
            toTemporaryBasal(profile)
                .toJson(isAdd, profile, dateUtil)
                ?.put("extendedEmulated", toRealJson(isAdd, dateUtil))
        else toRealJson(isAdd, dateUtil)
    }

fun EB.toRealJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("eventType", TE.Type.COMBO_BOLUS.text)
        .put("duration", T.msecs(duration).mins())
        .put("durationInMilliseconds", duration)
        .put("splitNow", 0)
        .put("splitExt", 100)
        .put("enteredinsulin", amount)
        .put("relative", rate)
        .put("isValid", isValid)
        .put("isEmulatingTempBasal", isEmulatingTempBasal)
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.endId != null) it.put("endId", ids.endId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }

fun EB.Companion.extendedBolusFromJson(jsonObject: JSONObject): EB? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    if (JsonHelper.safeGetIntAllowNull(jsonObject, "splitNow") != 0) return null
    if (JsonHelper.safeGetIntAllowNull(jsonObject, "splitExt") != 100) return null
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "enteredinsulin") ?: return null
    val duration = JsonHelper.safeGetLongAllowNull(jsonObject, "duration") ?: return null
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val isEmulatingTempBasal = JsonHelper.safeGetBoolean(jsonObject, "isEmulatingTempBasal", false)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val endPumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "endId", null)
    val pumpType = PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    if (duration == 0L && durationInMilliseconds == 0L) return null
    if (amount == 0.0) return null

    return EB(
        timestamp = timestamp,
        amount = amount,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        isEmulatingTempBasal = isEmulatingTempBasal,
        isValid = isValid
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.endId = endPumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}