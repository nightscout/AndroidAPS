package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.toTemporaryBasal
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.embedments.InterfaceIDs
import org.json.JSONObject

fun ExtendedBolus.toJson(isAdd: Boolean, profile: Profile?, dateUtil: DateUtil): JSONObject? =
    profile?.let {
        if (isEmulatingTempBasal)
            toTemporaryBasal(profile)
                .toJson(isAdd, profile, dateUtil)
                ?.put("extendedEmulated", toRealJson(isAdd, dateUtil))
        else toRealJson(isAdd, dateUtil)
    }

fun ExtendedBolus.toRealJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("eventType", TherapyEvent.Type.COMBO_BOLUS.text)
        .put("duration", T.msecs(duration).mins())
        .put("durationInMilliseconds", duration)
        .put("splitNow", 0)
        .put("splitExt", 100)
        .put("enteredinsulin", amount)
        .put("relative", rate)
        .put("isValid", isValid)
        .put("isEmulatingTempBasal", isEmulatingTempBasal)
        .also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.endId != null) it.put("endId", interfaceIDs.endId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

fun ExtendedBolus.Companion.extendedBolusFromJson(jsonObject: JSONObject): ExtendedBolus? {
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
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    if (duration == 0L && durationInMilliseconds == 0L) return null
    if (amount == 0.0) return null

    return ExtendedBolus(
        timestamp = timestamp,
        amount = amount,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        isEmulatingTempBasal = isEmulatingTempBasal,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.endId = endPumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}