package app.aaps.core.objects.extensions

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.DoublePreferenceKey
import app.aaps.core.keys.IntPreferenceKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringPreferenceKey
import app.aaps.core.keys.UnitDoublePreferenceKey
import org.json.JSONObject

fun JSONObject.put(key: IntPreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.put(key: DoublePreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.put(key: UnitDoublePreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.putString(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getString(key, "")) else this

fun JSONObject.put(key: StringPreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.put(key: BooleanPreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject =
    this.also { it.put(rh.gs(key.key), preferences.get(key)) }

fun JSONObject.store(key: IntPreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getInt(rh.gs(key.key)))
    return this
}

fun JSONObject.store(key: DoublePreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getDouble(rh.gs(key.key)))
    return this
}

fun JSONObject.store(key: UnitDoublePreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getDouble(rh.gs(key.key)))
    return this
}

fun JSONObject.storeString(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putString(key, getString(rh.gs(key)).toString())
    return this
}

fun JSONObject.store(key: StringPreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getString(rh.gs(key.key)))
    return this
}

fun JSONObject.storeBoolean(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key))) sp.putBoolean(key, getBoolean(rh.gs(key)))
    return this
}

fun JSONObject.store(key: BooleanPreferenceKey, preferences: Preferences, rh: ResourceHelper): JSONObject {
    if (has(rh.gs(key.key))) preferences.put(key, getBoolean(rh.gs(key.key)))
    return this
}

