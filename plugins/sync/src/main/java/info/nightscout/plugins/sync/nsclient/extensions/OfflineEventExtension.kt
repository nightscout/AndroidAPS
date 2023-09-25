package info.nightscout.plugins.sync.nsclient.extensions

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.embedments.InterfaceIDs
import org.json.JSONObject

fun OfflineEvent.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("eventType", TherapyEvent.Type.APS_OFFLINE.text)
        .put("isValid", isValid)
        .put("duration", T.msecs(duration).mins())
        .put("durationInMilliseconds", duration)
        .put("reason", reason.name)
        .also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

/* NS PS
{
    "enteredBy": "undefined",
    "eventType": "OpenAPS Offline",
    "duration": 60,
    "created_at": "2021-05-27T15:11:52.230Z",
    "utcOffset": 0,
    "_id": "60afb6ba3c0d77e3e720f2fe",
    "mills": 1622128312230,
    "carbs": null,
    "insulin": null
}
 */
fun OfflineEvent.Companion.fromJson(jsonObject: JSONObject): OfflineEvent? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)
    val reason = OfflineEvent.Reason.fromString(JsonHelper.safeGetString(jsonObject, "reason", OfflineEvent.Reason.OTHER.name))


    return OfflineEvent(
        timestamp = timestamp,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        isValid = isValid,
        reason = reason
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}
