package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.FD
import app.aaps.core.utils.JsonHelper

import org.json.JSONObject

fun FD.Companion.fromJson(jsonObject: JSONObject): FD? {
    if ("food" == JsonHelper.safeGetString(jsonObject, "type")) {
        val name = JsonHelper.safeGetStringAllowNull(jsonObject, "name", null) ?: return null
        val category = JsonHelper.safeGetStringAllowNull(jsonObject, "category", null)
        val subCategory = JsonHelper.safeGetStringAllowNull(jsonObject, "subcategory", null)
        val unit = JsonHelper.safeGetString(jsonObject, "unit", "")
        val portion = JsonHelper.safeGetDoubleAllowNull(jsonObject, "portion") ?: return null
        val carbs = JsonHelper.safeGetIntAllowNull(jsonObject, "carbs") ?: return null
        val gi = JsonHelper.safeGetIntAllowNull(jsonObject, "gi")
        val energy = JsonHelper.safeGetIntAllowNull(jsonObject, "energy")
        val protein = JsonHelper.safeGetIntAllowNull(jsonObject, "protein")
        val fat = JsonHelper.safeGetIntAllowNull(jsonObject, "fat")
        val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
            ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
            ?: return null
        val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)

        val food = FD(
            name = name,
            category = category,
            subCategory = subCategory,
            unit = unit,
            portion = portion,
            carbs = carbs,
            gi = gi,
            energy = energy,
            protein = protein,
            fat = fat,
            isValid = isValid
        )
        food.ids.nightscoutId = id
        return food
    }
    return null
}

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

