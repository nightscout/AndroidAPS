package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.FD
import org.json.JSONObject

fun FD.toJson(isAdd: Boolean): JSONObject =
    JSONObject()
        .put("type", "food")
        .put("name", name)
        .put("category", category)
        .put("subcategory", subCategory)
        .put("unit", unit)
        .put("portion", portion)
        .put("carbs", carbs)
        .put("gi", gi)
        .put("energy", energy)
        .put("protein", protein)
        .put("fat", fat)
        .put("isValid", isValid)
        .also { if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId) }

