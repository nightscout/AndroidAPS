package app.aaps.core.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Lenient readers that copy what Android's `org.json` does, so a document keeps parsing the same way
 * after it moves from `JSONObject` to [JsonObject].
 *
 * This matters more than it looks. `org.json` coerces on read and kotlinx does not:
 *
 * | stored value | `org.json` getInt | plain kotlinx `.int` |
 * |--------------|-------------------|----------------------|
 * | `36`         | 36                | 36                   |
 * | `"36"`       | 36                | throws               |
 * | `36.9`       | 36                | throws               |
 * | `"36.9"`     | 36                | throws               |
 * | `true`       | throws            | throws               |
 *
 * Quoted numbers are not a theoretical case - real Nightscout documents contain them, and AAPS has
 * always read them. A conversion that drops the coercion compiles, passes the current tests, and then
 * silently reads a stored value as its default. For a dosing field that is a real hazard: an
 * `insulinEndTime` that falls back to 0 means DIA 0, so IOB decays at once and the loop believes there
 * is no insulin on board.
 *
 * The rules below are Android's `JSON.toInteger` / `toLong` / `toDouble` / `toBoolean` / `toString`:
 * a number converts through its Java `intValue()`/`longValue()` (so fractions truncate toward zero),
 * a string converts through `Double.parseDouble` and is then narrowed, and anything else fails to the
 * default. A missing key and an explicit JSON `null` both give the default.
 */

/** The primitive at [key], or null when absent or explicitly JSON null. */
private fun JsonObject?.primitiveOrNull(key: String): JsonPrimitive? {
    val element: JsonElement = this?.get(key) ?: return null
    if (element is JsonNull) return null
    return element as? JsonPrimitive
}

/**
 * `true` only for a real quoted string. Numbers and booleans are unquoted, so this separates
 * `"36"` from `36` the way `org.json` separates a `String` value from a `Number` value.
 */
private fun JsonPrimitive.isQuoted(): Boolean = isString

/** Android `JSON.toDouble`: a number as-is, a quoted number through `parseDouble`, else null. */
private fun JsonPrimitive.asDoubleOrNull(): Double? = content.toDoubleOrNull()

fun JsonObject?.lenientInt(key: String, defaultValue: Int = 0): Int {
    val primitive = primitiveOrNull(key) ?: return defaultValue
    // A boolean is not a number for org.json, and "true".toDoubleOrNull() is null anyway.
    return primitive.content.toIntOrNull()
        ?: primitive.asDoubleOrNull()?.toInt()
        ?: defaultValue
}

fun JsonObject?.lenientLong(key: String, defaultValue: Long = 0L): Long {
    val primitive = primitiveOrNull(key) ?: return defaultValue
    // Whole digits are read exactly first. Only the fraction/exponent forms go through Double, which
    // is what org.json does for a quoted value - and every real epoch-millis value is below 2^53,
    // so that path is exact too.
    return primitive.content.toLongOrNull()
        ?: primitive.asDoubleOrNull()?.toLong()
        ?: defaultValue
}

fun JsonObject?.lenientDouble(key: String, defaultValue: Double = 0.0): Double {
    val primitive = primitiveOrNull(key) ?: return defaultValue
    return primitive.asDoubleOrNull() ?: defaultValue
}

/**
 * Android `JSON.toBoolean`: a real boolean, or the text `true`/`false` in any case. A number is NOT
 * a boolean here - `org.json` throws for `1`, so we fall back to [defaultValue].
 */
fun JsonObject?.lenientBoolean(key: String, defaultValue: Boolean): Boolean {
    val primitive = primitiveOrNull(key) ?: return defaultValue
    return when (primitive.content.lowercase()) {
        "true"  -> true
        "false" -> false
        else    -> defaultValue
    }
}

/**
 * Android `getString` coerces any non-null value to its text, so a stored `12` reads back as `"12"`.
 * An explicit JSON null gives [defaultValue] rather than the literal text `null` - that matches
 * `JsonHelper.safeGetString`, which maps `JSONObject.NULL.toString()` back to the default.
 */
fun JsonObject?.lenientString(key: String, defaultValue: String = ""): String {
    val primitive = primitiveOrNull(key) ?: return defaultValue
    return primitive.content
}

/** [lenientString] but null when absent, so callers can tell "missing" from "empty". */
fun JsonObject?.lenientStringOrNull(key: String): String? = primitiveOrNull(key)?.content

/*
 * Nullable variants, for the callers that must tell "absent or unreadable" from "present and zero".
 */

fun JsonObject?.lenientIntOrNull(key: String): Int? {
    val primitive = primitiveOrNull(key) ?: return null
    return primitive.content.toIntOrNull() ?: primitive.asDoubleOrNull()?.toInt()
}

fun JsonObject?.lenientLongOrNull(key: String): Long? {
    val primitive = primitiveOrNull(key) ?: return null
    return primitive.content.toLongOrNull() ?: primitive.asDoubleOrNull()?.toLong()
}

fun JsonObject?.lenientDoubleOrNull(key: String): Double? =
    primitiveOrNull(key)?.asDoubleOrNull()

fun JsonObject?.lenientBooleanOrNull(key: String): Boolean? =
    when (primitiveOrNull(key)?.content?.lowercase()) {
        "true"  -> true
        "false" -> false
        else    -> null
    }

/** True when a real quoted string is stored, for the few places that must not coerce a number. */
fun JsonObject?.holdsQuotedString(key: String): Boolean = primitiveOrNull(key)?.isQuoted() == true
