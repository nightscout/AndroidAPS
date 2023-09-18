package info.nightscout.plugins.sync.nsclient.extensions

import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.utils.JsonHelper
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject

fun EffectiveProfileSwitch.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("isValid", isValid)
        .put("eventType", info.nightscout.database.entities.TherapyEvent.Type.NOTE.text) // move to separate collection when available in NS
        .put("profileJson", ProfileSealed.EPS(this).toPureNsJson(dateUtil).toString())
        .put("originalProfileName", originalProfileName)
        .put("originalCustomizedName", originalCustomizedName)
        .put("originalTimeshift", originalTimeshift)
        .put("originalPercentage", originalPercentage)
        .put("originalDuration", originalDuration)
        .put("originalEnd", originalEnd)
        .put("notes", originalCustomizedName)
        .also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

fun EffectiveProfileSwitch.Companion.fromJson(jsonObject: JSONObject, dateUtil: DateUtil): EffectiveProfileSwitch? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val originalTimeshift = JsonHelper.safeGetLong(jsonObject, "originalTimeshift")
    val originalDuration = JsonHelper.safeGetLong(jsonObject, "originalDuration")
    val originalEnd = JsonHelper.safeGetLong(jsonObject, "originalEnd")
    val originalPercentage = JsonHelper.safeGetInt(jsonObject, "originalPercentage", 100)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
    val originalProfileName = JsonHelper.safeGetStringAllowNull(jsonObject, "originalProfileName", null) ?: return null
    val originalCustomizedName = JsonHelper.safeGetStringAllowNull(jsonObject, "originalCustomizedName", null) ?: return null
    val profileJson = JsonHelper.safeGetStringAllowNull(jsonObject, "profileJson", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    val pureProfile = pureProfileFromJson(JSONObject(profileJson), dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(pureProfile)

    return EffectiveProfileSwitch(
        timestamp = timestamp,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = EffectiveProfileSwitch.GlucoseUnit.fromConstant(profileSealed.units),
        originalProfileName = originalProfileName,
        originalCustomizedName = originalCustomizedName,
        originalTimeshift = originalTimeshift,
        originalPercentage = originalPercentage,
        originalDuration = originalDuration,
        originalEnd = originalEnd,
        insulinConfiguration = profileSealed.insulinConfiguration,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}

fun EffectiveProfileSwitch.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): EffectiveProfileSwitch.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) EffectiveProfileSwitch.GlucoseUnit.MGDL
    else EffectiveProfileSwitch.GlucoseUnit.MMOL

fun JSONObject.isEffectiveProfileSwitch() = has("originalProfileName")
