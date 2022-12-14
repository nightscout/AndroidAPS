package info.nightscout.core.utils.extensions

import androidx.annotation.StringRes
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject

fun JSONObject.putInt(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getInt(key, 0)) else this

fun JSONObject.putLong(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getLong(key, 0)) else this

fun JSONObject.putDouble(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getDouble(key, 0.0)) else this

fun JSONObject.putString(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getString(key, "")) else this

fun JSONObject.putBoolean(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getBoolean(key, false)) else this

fun JSONObject.storeInt(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getInt(rh.gs(key)).toString())
    return this
}

fun JSONObject.storeLong(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getLong(rh.gs(key)).toString())
    return this
}

fun JSONObject.storeDouble(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getDouble(rh.gs(key)).toString())
    return this
}

fun JSONObject.storeString(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getString(rh.gs(key)).toString())
    return this
}

fun JSONObject.storeBoolean(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getBoolean(rh.gs(key)).toString())
    return this
}
