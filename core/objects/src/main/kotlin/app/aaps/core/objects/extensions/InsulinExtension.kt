package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import org.json.JSONObject

fun ICfg.toJson(): JSONObject = JSONObject()
        .put("insulinLabel", insulinLabel)
        .put("insulinEndTime", insulinEndTime)
        .put("peak", peak)