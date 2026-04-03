package app.aaps.core.objects.extensions

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

fun JsonObject.put(key: IntPreferenceKey, preferences: Preferences): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(preferences.get(key))
        }
    )

fun JsonObject.put(key: LongPreferenceKey, preferences: Preferences): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(preferences.get(key))
        }
    )

fun JsonObject.put(key: DoublePreferenceKey, preferences: Preferences): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(preferences.get(key))
        }
    )

fun JsonObject.put(key: UnitDoublePreferenceKey, preferences: Preferences): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(preferences.get(key))
        }
    )

fun JsonObject.put(key: StringNonPreferenceKey, preferences: Preferences): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(preferences.get(key))
        }
    )

fun JsonObject.put(key: BooleanNonPreferenceKey, preferences: Preferences): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(preferences.get(key))
        }
    )

fun JsonObject.put(key: BooleanNonPreferenceKey, value: Boolean): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            this[key.key] = JsonPrimitive(value)
        }
    )

fun JsonObject.store(key: IntPreferenceKey, preferences: Preferences): JsonObject {
    if (contains(key.key)) preferences.put(key, (get(key.key) as JsonPrimitive).int)
    return this
}

fun JsonObject.store(key: LongPreferenceKey, preferences: Preferences): JsonObject {
    if (contains(key.key)) preferences.put(key, (get(key.key) as JsonPrimitive).long)
    return this
}

fun JsonObject.store(key: DoublePreferenceKey, preferences: Preferences): JsonObject {
    if (contains(key.key)) preferences.put(key, (get(key.key) as JsonPrimitive).double)
    return this
}

fun JsonObject.store(key: UnitDoublePreferenceKey, preferences: Preferences): JsonObject {
    if (contains(key.key)) preferences.put(key, (get(key.key) as JsonPrimitive).double)
    return this
}

fun JsonObject.store(key: StringNonPreferenceKey, preferences: Preferences): JsonObject {
    if (contains(key.key)) preferences.put(key, (get(key.key) as JsonPrimitive).content)
    return this
}

fun JsonObject.store(key: BooleanNonPreferenceKey, preferences: Preferences): JsonObject {
    if (contains(key.key)) preferences.put(key, (get(key.key) as JsonPrimitive).boolean)
    return this
}

fun JsonObject.putIfThereIsValue(key: String, value: Long?): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            if (value != null && value != 0L)
                this[key] = JsonPrimitive(value)
        }
    )

fun JsonObject.putIfThereIsValue(key: String, value: Double?): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            if (value != null && value != 0.0)
                this[key] = JsonPrimitive(value)
        }
    )

fun JsonObject.putIfThereIsValue(key: String, value: String?): JsonObject =
    JsonObject(
        this.toMutableMap().apply {
            if (value != null && value.isNotEmpty())
                this[key] = JsonPrimitive(value)
        }
    )
