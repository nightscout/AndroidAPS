package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun PS.toJson(isAdd: Boolean, dateUtil: DateUtil, decimalFormatter: DecimalFormatter): JSONObject =
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
        .put("eventType", TE.Type.PROFILE_SWITCH.text)
        .also { // remove customization to store original profileJson in toPureNsJson call
            timeshift = 0
            percentage = 100
        }
        .put("profileJson", ProfileSealed.PS(value = this, activePlugin = null).toPureNsJson(dateUtil).toString())
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
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
fun PS.Companion.fromJson(jsonObject: JSONObject, dateUtil: DateUtil, activePlugin: ActivePlugin): PS? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val originalDuration = JsonHelper.safeGetLongAllowNull(jsonObject, "originalDuration")
    val timeshift = JsonHelper.safeGetLong(jsonObject, "timeshift")
    val percentage = JsonHelper.safeGetInt(jsonObject, "percentage", 100)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val profileName = JsonHelper.safeGetStringAllowNull(jsonObject, "profile", null) ?: return null
    val originalProfileName = JsonHelper.safeGetStringAllowNull(jsonObject, "originalProfileName", null)
    val profileJson = JsonHelper.safeGetStringAllowNull(jsonObject, "profileJson", null)
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    val pureProfile =
        if (profileJson == null) { // entered through NS, no JSON attached
            val profilePlugin = activePlugin.activeProfileSource
            val store = profilePlugin.profile ?: return null
            store.getSpecificProfile(profileName) ?: return null
        } else pureProfileFromJson(JSONObject(profileJson), dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(value = pureProfile, activePlugin = null)

    return PS(
        timestamp = timestamp,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = profileSealed.units,
        profileName = originalProfileName ?: profileName,
        timeshift = timeshift,
        percentage = percentage,
        duration = originalDuration ?: T.mins(duration).msecs(),
        iCfg = profileSealed.iCfg,
        isValid = isValid
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}
