package app.aaps.core.objects.extensions

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import org.json.JSONObject

fun JSONObject.put(key: IntPreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.put(key: LongPreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.put(key: DoublePreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

fun JSONObject.put(key: UnitDoublePreferenceKey, preferences: Preferences): JSONObject =
    this.also { it.put(key.key, preferences.get(key)) }

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

