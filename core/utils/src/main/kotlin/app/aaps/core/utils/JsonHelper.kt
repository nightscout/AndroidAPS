package app.aaps.core.utils

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

    /**
     * Simple merge of two JSON objects.
     * @return new JSONObject with all keys from both objects
     */
    fun merge(base: JSONObject, changes: JSONObject?): JSONObject =
        JSONObject(base.toString()).also { json ->
            changes?.keys()?.forEach { key -> json.put(key, changes[key]) }
        }
}