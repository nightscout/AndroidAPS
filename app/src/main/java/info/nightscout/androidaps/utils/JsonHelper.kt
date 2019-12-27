package info.nightscout.androidaps.utils

import org.json.JSONException
import org.json.JSONObject

object JsonHelper {
    @JvmStatic
    fun safeGetObject(json: JSONObject?, fieldName: String, defaultValue: Any): Any {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json[fieldName]
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetJSONObject(json: JSONObject?, fieldName: String, defaultValue: JSONObject?): JSONObject? {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getJSONObject(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetString(json: JSONObject?, fieldName: String): String? {
        var result: String? = null
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getString(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetString(json: JSONObject?, fieldName: String, defaultValue: String): String {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getString(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetStringAllowNull(json: JSONObject?, fieldName: String, defaultValue: String?): String? {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getString(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetDouble(json: JSONObject?, fieldName: String): Double {
        var result = 0.0
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getDouble(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetDouble(json: JSONObject?, fieldName: String, defaultValue: Double): Double {
        var result = defaultValue
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getDouble(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetInt(json: JSONObject?, fieldName: String): Int {
        var result = 0
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getInt(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetLong(json: JSONObject?, fieldName: String): Long {
        var result: Long = 0
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getLong(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }

    @JvmStatic
    fun safeGetBoolean(json: JSONObject?, fieldName: String): Boolean {
        var result = false
        if (json != null && json.has(fieldName)) {
            try {
                result = json.getBoolean(fieldName)
            } catch (ignored: JSONException) {
            }
        }
        return result
    }
}