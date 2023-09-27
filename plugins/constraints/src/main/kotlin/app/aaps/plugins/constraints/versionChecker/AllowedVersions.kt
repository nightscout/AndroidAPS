package app.aaps.plugins.constraints.versionChecker

import org.joda.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AllowedVersions {

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