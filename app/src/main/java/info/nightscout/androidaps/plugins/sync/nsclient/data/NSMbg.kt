package info.nightscout.androidaps.plugins.sync.nsclient.data

import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class NSMbg(val json: JSONObject) {

    var date: Long = 0
    var mbg: Double = 0.0

    init {
        date = JsonHelper.safeGetLong(json, "mills")
        mbg = JsonHelper.safeGetDouble(json, "mgdl")
    }

    fun id(): String? = JsonHelper.safeGetStringAllowNull(json, "_id", null)
    fun isValid(): Boolean = date != 0L && mbg != 0.0
}