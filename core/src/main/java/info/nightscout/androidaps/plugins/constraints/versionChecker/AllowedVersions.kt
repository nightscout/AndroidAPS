package info.nightscout.androidaps.plugins.constraints.versionChecker

import org.joda.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception

class AllowedVersions {

    fun generateSupportedVersions(): String =
        JSONArray()
            // Android API versions
            .put(JSONObject().apply {
                put("minAndroid", 1) // 1.0
                put("maxAndroid", 23) // 6.0.1
            })
            .put(JSONObject().apply {
                put("minAndroid", 24) // 7.0
                put("maxAndroid", 25) // 7.1.2
                put("supported", "2.6.2")
            })
            .put(JSONObject().apply {
                put("minAndroid", 26) // 8.0
                put("maxAndroid", 27) // 8.1
                put("supported", "2.8.2")
            })
            .put(JSONObject().apply {
                put("minAndroid", 28) // 9.0
                put("maxAndroid", 99)
                put("supported", "2.8.2")
            })
            // Version time limitation
            .put(JSONObject().apply {
                put("endDate", "2021-11-07")
                put("version", "2.9.0-beta1")
            })
            .put(JSONObject().apply {
                put("endDate", "2021-11-07")
                put("version", "3.0-beta1")
            })
            .toString()

    fun findByApi(definition: String?, api: Int): JSONObject? {
        if (definition == null) return null
        try {
            val array = JSONArray(definition)
            for (i in 0 until array.length()) {
                val record = array[i] as JSONObject
                if (record.has("minAndroid") && record.has("maxAndroid"))
                    if (api in record.getInt("minAndroid")..record.getInt("maxAndroid")) return record
            }
        } catch (e: JSONException) {
        }
        return null
    }

    fun findByVersion(definition: String?, version: String): JSONObject? {
        if (definition == null) return null
        try {
            val array = JSONArray(definition)
            for (i in 0 until array.length()) {
                val record = array[i] as JSONObject
                if (record.has("endDate") && record.has("version"))
                    if (version == record.getString("version")) return record
            }
        } catch (e: JSONException) {
        }
        return null
    }

    fun endDateToMilliseconds(endDate: String): Long? {
        try {
            val dateTime = LocalDate.parse(endDate)
            return dateTime.toDate().time
        } catch (ignored: Exception) {
        }
        return null
    }
}