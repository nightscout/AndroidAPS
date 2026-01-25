package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun EPS.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("isValid", isValid)
        .put("eventType", TE.Type.NOTE.text) // move to separate collection when available in NS
        .put("profileJson", ProfileSealed.EPS(value = this, activePlugin = null).toPureNsJson(dateUtil).toString())
        .put("originalProfileName", originalProfileName)
        .put("originalCustomizedName", originalCustomizedName)
        .put("originalTimeshift", originalTimeshift)
        .put("originalPercentage", originalPercentage)
        .put("originalDuration", originalDuration)
        .put("originalEnd", originalEnd)
        .put("notes", originalCustomizedName)
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }

fun EPS.Companion.fromJson(jsonObject: JSONObject, dateUtil: DateUtil, insulinFallback: Insulin): EPS? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val originalTimeshift = JsonHelper.safeGetLong(jsonObject, "originalTimeshift")
    val originalDuration = JsonHelper.safeGetLong(jsonObject, "originalDuration")
    val originalEnd = JsonHelper.safeGetLong(jsonObject, "originalEnd")
    val originalPercentage = JsonHelper.safeGetInt(jsonObject, "originalPercentage", 100)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val originalProfileName = JsonHelper.safeGetStringAllowNull(jsonObject, "originalProfileName", null) ?: return null
    val originalCustomizedName = JsonHelper.safeGetStringAllowNull(jsonObject, "originalCustomizedName", null) ?: return null
    val profileJson = JsonHelper.safeGetStringAllowNull(jsonObject, "profileJson", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    val insulinLabel = JsonHelper.safeGetStringAllowNull(jsonObject, "insulinLabel", null)
    val insulinEndTime = JsonHelper.safeGetLongAllowNull(jsonObject, "insulinEndTime")
    val insulinPeakTime = JsonHelper.safeGetLongAllowNull(jsonObject, "insulinPeakTime")
    val concentration = JsonHelper.safeGetDoubleAllowNull(jsonObject, "concentration")

    val iCfg =
        if (insulinLabel != null && insulinEndTime != null && insulinPeakTime != null && concentration != null) ICfg(insulinLabel, insulinEndTime, insulinPeakTime, concentration)
        else ICfg(
            insulinLabel = insulinFallback.friendlyName,
            insulinEndTime = (insulinFallback.dia * 60 * 60 * 1000).toLong(),
            insulinPeakTime = (insulinFallback.peak * 60 * 1000).toLong(),
            concentration = 1.0
        )

    if (timestamp == 0L) return null
    val pureProfile = pureProfileFromJson(JSONObject(profileJson), dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(value = pureProfile, activePlugin = null)

    return EPS(
        timestamp = timestamp,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = profileSealed.units,
        originalProfileName = originalProfileName,
        originalCustomizedName = originalCustomizedName,
        originalTimeshift = originalTimeshift,
        originalPercentage = originalPercentage,
        originalDuration = originalDuration,
        originalEnd = originalEnd,
        iCfg = iCfg,
        isValid = isValid
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}

fun JSONObject.isEffectiveProfileSwitch() = has("originalProfileName")
