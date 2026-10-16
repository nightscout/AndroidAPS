package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.profile.ProfileSealed
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
        .also { if (originalPsId != null) it.put("originalPsId", originalPsId) }
        .put("notes", originalCustomizedName)
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }
