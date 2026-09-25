package app.aaps.core.utils

import kotlinx.serialization.json.JsonObject

/**
 * The kotlinx JSON readers, split out of `JsonHelper`.
 *
 * `JsonHelper` is an `object` in androidMain holding the `org.json` originals, and a Kotlin object
 * cannot be split across source sets - so the kotlinx extensions live here, where commonMain code can
 * reach them. Each one delegates to the `lenient*` readers in `JsonLenientRead.kt`.
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
 * They must NOT call the strict kotlinx accessors (.int, .long, .double, .boolean) inside a
 * try/catch that swallows the failure and returns the default. That looks equivalent to the
 * org.json halves above and is not: org.json coerces on read, kotlinx throws. A stored "36"
 * (a quoted number, which real Nightscout documents contain) reads back as 36 through org.json
 * and would be the DEFAULT here, with nothing logged. For a dosing field that is a hazard - the
 * same one that makes ICfg.fromJsonObject read insulinEndTime as 0, i.e. DIA 0.
 *
 * The string overloads need the same care for a second reason: JsonNull IS a JsonPrimitive whose
 * content is "null", so reading it as a primitive yields the literal text "null" where the
 * org.json halves return the default.
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

