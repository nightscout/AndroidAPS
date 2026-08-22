package app.aaps.core.utils

import kotlinx.serialization.json.JsonObject
import org.json.JSONException
import org.json.JSONObject

object JsonHelper {

    fun safeGetObject(json: JSONObject?, fieldName: String, defaultValue: Any): Any {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json[fieldName]
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetJSONObject(json: JSONObject?, fieldName: String, defaultValue: JSONObject?): JSONObject? {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getJSONObject(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetString(json: JSONObject?, fieldName: String): String? {
        var result: String? = null
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getString(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetString(json: JSONObject?, fieldName: String, defaultValue: String): String {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getString(fieldName)
                if (result == JSONObject.NULL.toString()) result = defaultValue
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetStringAllowNull(json: JSONObject?, fieldName: String, defaultValue: String?): String? {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getString(fieldName)
                if (result == JSONObject.NULL.toString()) result = defaultValue
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetDouble(json: JSONObject?, fieldName: String): Double {
        var result = 0.0
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getDouble(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetDoubleAllowNull(json: JSONObject?, fieldName: String): Double? {
        var result: Double? = null
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getDouble(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetDouble(json: JSONObject?, fieldName: String, defaultValue: Double): Double {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getDouble(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetInt(json: JSONObject?, fieldName: String): Int =
        safeGetInt(json, fieldName, 0)

    fun safeGetInt(json: JSONObject?, fieldName: String, defaultValue: Int): Int {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getInt(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetIntAllowNull(json: JSONObject?, fieldName: String): Int? {
        var result: Int? = null
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getInt(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetLong(json: JSONObject?, fieldName: String): Long {
        var result: Long = 0
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getLong(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetLongAllowNull(json: JSONObject?, fieldName: String, defaultValue: Long? = null): Long? {
        var result: Long? = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getLong(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetBoolean(json: JSONObject?, fieldName: String, defaultValue: Boolean = false): Boolean {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getBoolean(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    fun safeGetBooleanAllowNull(json: JSONObject?, fieldName: String, defaultValue: Boolean? = null): Boolean? {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getBoolean(fieldName)
            } catch (_: JSONException) {
            }
        }
        return result
    }

    // Kotlin serialized
    // fun safeGetObject(json: JsonObject?, fieldName: String, defaultValue: Any): Any {
    //     var result = defaultValue
    //     if (json != null && json.contains(fieldName)) {
    //         try {
    //             result = json[fieldName]
    //         } catch (_: JSONException) {
    //         }
    //     }
    //     return result
    // }

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

    /**
     * Simple merge of two JSON objects.
     * @return new JSONObject with all keys from both objects
     */
    fun merge(base: JSONObject, changes: JSONObject?): JSONObject =
        JSONObject(base.toString()).also { json ->
            changes?.keys()?.forEach { key -> json.put(key, changes[key]) }
        }
}