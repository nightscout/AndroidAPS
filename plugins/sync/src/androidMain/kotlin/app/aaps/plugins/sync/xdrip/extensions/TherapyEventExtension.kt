package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import org.json.JSONObject

fun TE.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", type.text)
        .put("isValid", isValid)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", enteredBy)
        .put("units", if (glucoseUnit == GlucoseUnit.MGDL) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
        .also {
            if (duration != 0L) it.put("duration", T.msecs(duration).mins())
            if (duration != 0L) it.put("durationInMilliseconds", duration)
            if (note != null) it.put("notes", note)
            if (glucose != null) it.put("glucose", glucose)
            if (glucoseType != null) it.put("glucoseType", glucoseType!!.text)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
            if (type == TE.Type.ANNOUNCEMENT) it.put("isAnnouncement", true)
            if (location != null) it.put("location", location?.text)
            if (arrow != null) it.put("arrow", arrow?.text)
        }