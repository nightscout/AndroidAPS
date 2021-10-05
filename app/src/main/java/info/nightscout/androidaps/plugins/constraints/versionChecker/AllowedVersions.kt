package info.nightscout.androidaps.plugins.constraints.versionChecker

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AllowedVersions {

    fun generateSupportedVersions(): String =
        JSONArray()
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
            .toString()

    fun findByApi(definition: String?, api: Int): JSONObject? {
        if (definition == null) return null
        try {
            val array = JSONArray(definition)
            for (i in 0 until array.length()) {
                val record = array[i] as JSONObject
                if (api in record.getInt("minAndroid")..record.getInt("maxAndroid")) return record
            }
        } catch (e: JSONException) {
        }
        return null
    }

}