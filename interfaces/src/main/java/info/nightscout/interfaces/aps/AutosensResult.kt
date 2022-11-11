package info.nightscout.interfaces.aps

import org.json.JSONObject

class AutosensResult {

    //default values to show when autosens algorithm is not called
    var ratio = 1.0
    var carbsAbsorbed = 0.0
    var sensResult = "autosens not available"
    var pastSensitivity = ""
    var ratioLimit = ""

    fun json(): JSONObject = JSONObject()
        .put("ratio", ratio)
        .put("ratioLimit", ratioLimit)
        .put("pastSensitivity", pastSensitivity)
        .put("sensResult", sensResult)
        .put("ratio", ratio)

    override fun toString(): String = json().toString()
}