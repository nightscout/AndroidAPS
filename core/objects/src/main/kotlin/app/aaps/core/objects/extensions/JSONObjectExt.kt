package app.aaps.core.objects.extensions

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import org.json.JSONObject

fun JSONObject.putInt(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getInt(key, 0)) else this

fun JSONObject.put(key: IntKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.putLong(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getLong(key, 0)) else this

fun JSONObject.putDouble(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getDouble(key, 0.0)) else this

fun JSONObject.put(key: DoubleKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.put(key: UnitDoubleKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.putString(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getString(key, "")) else this

fun JSONObject.put(key: StringKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.putBoolean(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getBoolean(key, false)) else this

fun JSONObject.put(key: BooleanKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.storeInt(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getInt(rh.gs(key)).toString())
    return this
}

fun JSONObject.store(key: IntKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getInt(rh.gs(key.key)))
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

fun JSONObject.store(key: DoubleKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getDouble(rh.gs(key.key)))
    return this
}

fun JSONObject.store(key: UnitDoubleKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getDouble(rh.gs(key.key)))
    return this
}

fun JSONObject.storeString(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getString(rh.gs(key)).toString())
    return this
}

fun JSONObject.store(key: StringKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getString(rh.gs(key.key)))
    return this
}

fun JSONObject.storeBoolean(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putBoolean(key, getBoolean(rh.gs(key)))
    return this
}

fun JSONObject.store(key: BooleanKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getBoolean(rh.gs(key.key)))
    return this
}

