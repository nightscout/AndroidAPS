package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import org.json.JSONObject

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
