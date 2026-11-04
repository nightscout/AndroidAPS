package app.aaps.core.objects.extensions

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.utils.lenientBooleanOrNull
import app.aaps.core.utils.lenientDoubleOrNull
import app.aaps.core.utils.lenientIntOrNull
import app.aaps.core.utils.lenientLongOrNull
import app.aaps.core.utils.lenientStringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun JsonObject.put(key: NonPreferenceKey, preferences: Preferences): JsonObject {
    val primitive: JsonPrimitive = when (key) {
        is IntNonPreferenceKey     -> JsonPrimitive(preferences.get(key))
        is LongNonPreferenceKey    -> JsonPrimitive(preferences.get(key))
        is DoubleNonPreferenceKey  -> JsonPrimitive(preferences.get(key))
        is UnitDoublePreferenceKey -> JsonPrimitive(preferences.get(key))
        is StringNonPreferenceKey  -> JsonPrimitive(preferences.get(key))
        is BooleanNonPreferenceKey -> JsonPrimitive(preferences.get(key))
        else                       -> error("Unsupported key type: ${key::class.simpleName}")
    }
    return JsonObject(this.toMutableMap().apply { this[key.key] = primitive })
}

fun JsonObject.put(key: BooleanNonPreferenceKey, value: Boolean): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(value)
        }
    )

/**
 * Reads leniently, the way the `org.json` twin does.
 *
 * The strict kotlinx accessors (`raw.int`, `raw.double`, `raw.boolean`) throw for a quoted number,
 * while `org.json`'s `getInt` / `getDouble` / `getBoolean` coerce it - `"36"`, `36.9` and `"36.9"` all
 * read back as 36 there. A value that cannot be read at all still throws, because the `org.json`
 * version has no catch either and callers rely on an unreadable document failing loudly.
 */
fun JsonObject.store(key: NonPreferenceKey, preferences: Preferences): JsonObject {
    if (!contains(key.key)) return this
    fun unreadable(): Nothing = error("Cannot read ${key.key} as ${key::class.simpleName}")
    when (key) {
        is IntNonPreferenceKey     -> preferences.put(key, lenientIntOrNull(key.key) ?: unreadable())
        is LongNonPreferenceKey    -> preferences.put(key, lenientLongOrNull(key.key) ?: unreadable())
        is DoubleNonPreferenceKey  -> preferences.put(key, lenientDoubleOrNull(key.key) ?: unreadable())
        is UnitDoublePreferenceKey -> preferences.put(key, lenientDoubleOrNull(key.key) ?: unreadable())
        is StringNonPreferenceKey  -> preferences.put(key, lenientStringOrNull(key.key) ?: unreadable())
        is BooleanNonPreferenceKey -> preferences.put(key, lenientBooleanOrNull(key.key) ?: unreadable())
        else                       -> error("Unsupported key type: ${key::class.simpleName}")
    }
    return this
}

/**
 * kotlinx twins of the `org.json` `putIfThereIsValue`, with the same rule: skip nulls **and** zeros.
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
