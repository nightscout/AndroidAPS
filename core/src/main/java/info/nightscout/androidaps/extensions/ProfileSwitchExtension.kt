package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import java.util.*

fun ProfileSwitch.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("isValid", isValid)
        .put("eventType", TherapyEvent.Type.PROFILE_SWITCH.text)
        .put("duration", T.msecs(duration).mins())
        .put("profile", getCustomizedName())
        .put("profileJson", ProfileSealed.PS(this).toPureNsJson(dateUtil).toString())
        .put("timeshift", 0)
        .put("percentage", 100) // customization already applied to json
        .also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

/* NS PS
{
   "_id":"608ffa268db0676196a772d7",
   "enteredBy":"undefined",
   "eventType":"Profile Switch",
   "duration":10,
   "profile":"LocalProfile0",
   "created_at":"2021-05-03T13:26:58.537Z",
   "utcOffset":0,
   "mills":1620048418537,
   "mgdl":98
}
 */
fun profileSwitchFromJson(jsonObject: JSONObject, dateUtil: DateUtil, activePlugin: ActivePlugin): ProfileSwitch? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val timeshift = JsonHelper.safeGetLong(jsonObject, "timeshift")
    val percentage = JsonHelper.safeGetInt(jsonObject, "percentage", 100)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
    val profileName = JsonHelper.safeGetStringAllowNull(jsonObject, "profile", null) ?: return null
    val profileJson = JsonHelper.safeGetStringAllowNull(jsonObject, "profileJson", null)
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    val pureProfile =
        if (profileJson == null) { // entered through NS, no JSON attached
            val profilePlugin = activePlugin.activeProfileSource
            val store = profilePlugin.profile ?: return null
            store.getSpecificProfile(profileName) ?: return null
        } else pureProfileFromJson(JSONObject(profileJson), dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(pureProfile)


    return ProfileSwitch(
        timestamp = timestamp,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = ProfileSwitch.GlucoseUnit.fromConstant(profileSealed.units),
        profileName = profileName,
        timeshift = T.hours(timeshift).msecs(),
        percentage = percentage,
        duration = T.mins(duration).msecs(),
        insulinConfiguration = profileSealed.insulinConfiguration,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}

/**
 * Pure profile doesn't contain timestamp, percentage, timeshift, profileName
 */
fun pureProfileFromJson(jsonObject: JSONObject, dateUtil: DateUtil, defaultUnits: String? = null): PureProfile? {
    try {
        JsonHelper.safeGetStringAllowNull(jsonObject, "units", defaultUnits) ?: return null
        val units = GlucoseUnit.fromText(JsonHelper.safeGetString(jsonObject, "units", Constants.MGDL))
        val dia = JsonHelper.safeGetDoubleAllowNull(jsonObject, "dia") ?: return null
        val timezone = TimeZone.getTimeZone(JsonHelper.safeGetString(jsonObject, "timezone", "UTC"))

        val isfBlocks = blockFromJsonArray(jsonObject.getJSONArray("sens"), dateUtil) ?: return null
        val icBlocks = blockFromJsonArray(jsonObject.getJSONArray("carbratio"), dateUtil)
            ?: return null
        val basalBlocks = blockFromJsonArray(jsonObject.getJSONArray("basal"), dateUtil)
            ?: return null
        val targetBlocks = targetBlockFromJsonArray(jsonObject.getJSONArray("target_low"), jsonObject.getJSONArray("target_high"), dateUtil)
            ?: return null

        return PureProfile(
            jsonObject = jsonObject,
            basalBlocks = basalBlocks,
            isfBlocks = isfBlocks,
            icBlocks = icBlocks,
            targetBlocks = targetBlocks,
            glucoseUnit = units,
            timeZone = timezone,
            dia = dia
        )
    } catch (ignored: Exception) {
        return null
    }
}

fun ProfileSwitch.getCustomizedName(): String {
    var name: String = profileName
    if (Constants.LOCAL_PROFILE == name) {
        name = to2Decimal(ProfileSealed.PS(this).percentageBasalSum()) + "U "
    }
    if (timeshift != 0L || percentage != 100) {
        name += "($percentage%"
        if (timeshift != 0L) name += "," + T.msecs(timeshift).hours() + "h"
        name += ")"
    }
    return name
}

fun ProfileSwitch.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): ProfileSwitch.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) ProfileSwitch.GlucoseUnit.MGDL
    else ProfileSwitch.GlucoseUnit.MMOL


