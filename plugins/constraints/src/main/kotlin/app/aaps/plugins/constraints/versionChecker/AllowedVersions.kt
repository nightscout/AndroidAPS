package app.aaps.plugins.constraints.versionChecker

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AllowedVersions {

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
            return null
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
            return null
        }
        return null
    }

    fun endDateToMilliseconds(endDate: String): Long? =
        try {
            val date = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            LocalDateTime.of(date, LocalTime.of(0, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (ignored: Exception) {
            null
        }
}
