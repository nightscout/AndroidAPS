package info.nightscout.plugins.sync.nsclient.extensions

import info.nightscout.core.extensions.fromConstant
import info.nightscout.core.extensions.getCustomizedName
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.utils.JsonHelper
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.json.JSONObject

fun ProfileSwitch.toJson(isAdd: Boolean, dateUtil: DateUtil, decimalFormatter: DecimalFormatter): JSONObject =
    JSONObject()
        .put("timeshift", timeshift)
        .put("percentage", percentage)
        .put("duration", T.msecs(duration).mins())
        .put("profile", getCustomizedName(decimalFormatter))
        .put("originalProfileName", profileName)
        .put("originalDuration", duration)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("isValid", isValid)
        .put("eventType", TherapyEvent.Type.PROFILE_SWITCH.text)
        .also { // remove customization to store original profileJson in toPureNsJson call
            timeshift = 0
            percentage = 100
        }
        .put("profileJson", ProfileSealed.PS(this).toPureNsJson(dateUtil).toString())
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
fun ProfileSwitch.Companion.fromJson(jsonObject: JSONObject, dateUtil: DateUtil, activePlugin: ActivePlugin): ProfileSwitch? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val originalDuration = JsonHelper.safeGetLongAllowNull(jsonObject, "originalDuration")
    val timeshift = JsonHelper.safeGetLong(jsonObject, "timeshift")
    val percentage = JsonHelper.safeGetInt(jsonObject, "percentage", 100)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
    val profileName = JsonHelper.safeGetStringAllowNull(jsonObject, "profile", null) ?: return null
    val originalProfileName = JsonHelper.safeGetStringAllowNull(jsonObject, "originalProfileName", null)
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
        profileName = originalProfileName ?: profileName,
        timeshift = timeshift,
        percentage = percentage,
        duration = originalDuration ?: T.mins(duration).msecs(),
        insulinConfiguration = profileSealed.insulinConfiguration,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}
