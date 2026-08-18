package app.aaps.core.objects.extensions

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

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

fun JsonObject.store(key: NonPreferenceKey, preferences: Preferences): JsonObject {
    if (!contains(key.key)) return this
    val raw = get(key.key) as JsonPrimitive
    when (key) {
        is IntNonPreferenceKey     -> preferences.put(key, raw.int)
        is LongNonPreferenceKey    -> preferences.put(key, raw.long)
        is DoubleNonPreferenceKey  -> preferences.put(key, raw.double)
        is UnitDoublePreferenceKey -> preferences.put(key, raw.double)
        is StringNonPreferenceKey  -> preferences.put(key, raw.content)
        is BooleanNonPreferenceKey -> preferences.put(key, raw.boolean)
        else                       -> error("Unsupported key type: ${key::class.simpleName}")
    }
    return this
}
