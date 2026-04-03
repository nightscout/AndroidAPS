package app.aaps.core.interfaces.nsclient

import org.json.JSONObject

interface NSSettingsStatus {

    fun handleNewData(status: JSONObject)
    fun getVersion(): String
    fun extendedPumpSettings(setting: String?): Double
    fun pumpExtendedSettingsFields(): String
    fun getExtendedWarnValue(plugin: String, property: String): Double?
}