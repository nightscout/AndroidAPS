package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.database.entities.Food
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

fun foodFromJson(jsonObject: JSONObject): Food? {
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
        val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
        val isValid = JsonHelper.safeGetBoolean(jsonObject, NSUpload.ISVALID, true)

        val food = Food(
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
        food.interfaceIDs.nightscoutId = id
        return food
    }
    return null
}
