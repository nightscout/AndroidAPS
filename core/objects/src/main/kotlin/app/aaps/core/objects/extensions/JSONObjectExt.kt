package app.aaps.core.objects.extensions

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanNonPreferenceKey
import app.aaps.core.keys.DoublePreferenceKey
import app.aaps.core.keys.IntPreferenceKey
import app.aaps.core.keys.LongPreferenceKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringNonPreferenceKey
import app.aaps.core.keys.UnitDoublePreferenceKey
import org.json.JSONObject

fun JSONObject.put(key: IntPreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.put(key: LongPreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.put(key: DoublePreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.put(key: UnitDoublePreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.putString(@StringRes key: Int, sp: SP, rh: ResourceHelper): JSONObject =
    if (sp.contains(key)) put(rh.gs(key), sp.getString(key, "")) else this

fun JSONObject.put(key: StringNonPreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.put(key: BooleanNonPreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.store(key: IntPreferenceKey, preferences: Preferences): JSONObject {
    if (has(key.key)) preferences.put(key, getInt(key.key))
    return this
}

fun JSONObject.store(key: LongPreferenceKey, preferences: Preferences): JSONObject {
    if (has(key.key)) preferences.put(key, getLong(key.key))
    return this
}

fun JSONObject.store(key: DoublePreferenceKey, preferences: Preferences): JSONObject {
    if (has(key.key)) preferences.put(key, getDouble(key.key))
    return this
}

fun JSONObject.store(key: UnitDoublePreferenceKey, preferences: Preferences): JSONObject {
    if (has(key.key)) preferences.put(key, getDouble(key.key))
    return this
}

fun JSONObject.store(key: StringNonPreferenceKey, preferences: Preferences): JSONObject {
    if (has(key.key)) preferences.put(key, getString(key.key))
    return this
}

fun JSONObject.store(key: BooleanNonPreferenceKey, preferences: Preferences): JSONObject {
    if (has(key.key)) preferences.put(key, getBoolean(key.key))
    return this
}

