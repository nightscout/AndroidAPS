package app.aaps.core.objects.extensions

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

fun JSONObject.putIfThereIsValue(key: String, value: Int?): JSONObject =
    this.also { if (value != null && value != 0) it.put(key, value) }

fun JSONObject.putIfThereIsValue(key: String, value: Long?): JSONObject =
    this.also { if (value != null && value != 0L) it.put(key, value) }

fun JSONObject.putIfThereIsValue(key: String, value: Double?): JSONObject =
    this.also { if (value != null && value != 0.0) it.put(key, value) }

fun JSONObject.putIfThereIsValue(key: String, value: String?): JSONObject =
    this.also { if (value != null && value.isNotEmpty()) it.put(key, value) }

/**
 * kotlinx twins of [putIfThereIsValue], with the same rule: skip nulls **and** zeros.
 *
 * The zero-skipping is not cosmetic. It is what keeps an idle temp basal or extended bolus out of the
 * Nightscout device status entirely, rather than reporting a rate of 0 U/h.
 */
fun JsonObjectBuilder.putIfThereIsValue(key: String, value: Int?) {
    if (value != null && value != 0) put(key, value)
}

fun JsonObjectBuilder.putIfThereIsValue(key: String, value: Long?) {
    if (value != null && value != 0L) put(key, value)
}

fun JsonObjectBuilder.putIfThereIsValue(key: String, value: Double?) {
    if (value != null && value != 0.0) put(key, value)
}

fun JsonObjectBuilder.putIfThereIsValue(key: String, value: String?) {
    if (value != null && value.isNotEmpty()) put(key, value)
}

/**
 * Copy of this document with [extra] entries added on top.
 *
 * A [JsonObject] cannot be written into after it is built, so code that used to take a finished
 * document and keep putting into it needs a new document instead. Entries added here win over
 * entries of the same name already present, which is what writing into the old object did.
 */
fun JsonObject.with(extra: JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject {
        this@with.forEach { (key, value) -> put(key, value) }
        extra()
    }
