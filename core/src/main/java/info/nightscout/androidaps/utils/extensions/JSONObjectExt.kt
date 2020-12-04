package info.nightscout.androidaps.utils.extensions

import androidx.annotation.StringRes
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONObject

fun JSONObject.putInt(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(resourceHelper.gs(key), sp.getInt(key, 0)) else this

fun JSONObject.putLong(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(resourceHelper.gs(key), sp.getLong(key, 0)) else this

fun JSONObject.putDouble(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(resourceHelper.gs(key), sp.getDouble(key, 0.0)) else this

fun JSONObject.putString(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(resourceHelper.gs(key), sp.getString(key, "")) else this

fun JSONObject.putBoolean(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(resourceHelper.gs(key), sp.getBoolean(key, false)) else this

fun JSONObject.storeInt(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject {
    if (has(resourceHelper.gs(key))) sp.putInt(key, getInt(resourceHelper.gs(key)))
    return this
}

fun JSONObject.storeLong(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject {
    if (has(resourceHelper.gs(key))) sp.putLong(key, getLong(resourceHelper.gs(key)))
    return this
}

fun JSONObject.storeDouble(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject {
    if (has(resourceHelper.gs(key))) sp.putDouble(key, getDouble(resourceHelper.gs(key)))
    return this
}

fun JSONObject.storeString(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject {
    if (has(resourceHelper.gs(key))) sp.putString(key, getString(resourceHelper.gs(key)))
    return this
}

fun JSONObject.storeBoolean(@StringRes key: Int, sp: SP, resourceHelper: ResourceHelper): JSONObject {
    if (has(resourceHelper.gs(key))) sp.putBoolean(key, getBoolean(resourceHelper.gs(key)))
    return this
}


