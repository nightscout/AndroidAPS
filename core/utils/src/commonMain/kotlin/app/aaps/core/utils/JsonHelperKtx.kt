package app.aaps.core.utils

import kotlinx.serialization.json.JsonObject

/**
 * The kotlinx half of what used to be `JsonHelper`.
 *
 * `JsonHelper` is an `object` in androidMain holding both these and the `org.json` originals, and a
 * Kotlin object cannot be split across source sets - so the kotlinx extensions moved out here, where
 * commonMain code can reach them. The `org.json` ones stay behind; nothing multiplatform uses them.
 *
 * The bodies are unchanged: each one delegates to the `lenient*` readers in `JsonLenientRead.kt`,
 * which were already in commonMain.
 */
fun JsonObject?.safeGetJSONObject(fieldName: String, defaultValue: JsonObject?): JsonObject? {
    var result = defaultValue
    if (this?.contains(fieldName) == true) {
        try {
            result = this[fieldName] as JsonObject
        } catch (_: Exception) {
        }
    }
    return result
}

/*
 * The kotlinx twins below delegate to the lenient readers in JsonLenientRead.
 *
 * They used to call the strict kotlinx accessors (.int, .long, .double, .boolean) inside a
 * try/catch that swallowed the failure and returned the default. That looked equivalent to the
 * org.json halves above and was not: org.json coerces on read, kotlinx throws. A stored "36"
 * (a quoted number, which real Nightscout documents contain) read back as 36 through org.json
 * and as the DEFAULT through these, with nothing logged. For a dosing field that is a hazard -
 * the same one that made ICfg.fromJsonObject read insulinEndTime as 0, i.e. DIA 0.
 *
 * The two string overloads had a second, separate bug: `if (get(fieldName) is JsonNull) result =
 * defaultValue` was immediately overwritten by an unconditional assignment on the next line, and
 * since JsonNull IS a JsonPrimitive whose content is "null", they returned the literal text
 * "null" where the org.json halves return the default.
 */

fun JsonObject?.safeGetString(fieldName: String): String? =
    lenientStringOrNull(fieldName)

fun JsonObject?.safeGetString(fieldName: String, defaultValue: String): String =
    lenientString(fieldName, defaultValue)

fun JsonObject?.safeGetStringAllowNull(fieldName: String, defaultValue: String?): String? =
    lenientStringOrNull(fieldName) ?: defaultValue

fun JsonObject?.safeGetDouble(fieldName: String): Double =
    lenientDouble(fieldName, 0.0)

fun JsonObject?.safeGetDoubleAllowNull(fieldName: String): Double? =
    lenientDoubleOrNull(fieldName)

fun JsonObject?.safeGetDouble(fieldName: String, defaultValue: Double): Double =
    lenientDouble(fieldName, defaultValue)

fun JsonObject?.safeGetInt(fieldName: String): Int =
    safeGetInt(fieldName, 0)

fun JsonObject?.safeGetInt(fieldName: String, defaultValue: Int): Int =
    lenientInt(fieldName, defaultValue)

fun JsonObject?.safeGetIntAllowNull(fieldName: String): Int? =
    lenientIntOrNull(fieldName)

fun JsonObject?.safeGetLong(fieldName: String): Long =
    lenientLong(fieldName, 0)

fun JsonObject?.safeGetLongAllowNull(fieldName: String, defaultValue: Long? = null): Long? =
    lenientLongOrNull(fieldName) ?: defaultValue

fun JsonObject?.safeGetBoolean(fieldName: String, defaultValue: Boolean = false): Boolean =
    lenientBoolean(fieldName, defaultValue)

fun JsonObject?.safeGetBooleanAllowNull(fieldName: String, defaultValue: Boolean? = null): Boolean? =
    lenientBooleanOrNull(fieldName) ?: defaultValue

