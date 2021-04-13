package info.nightscout.androidaps.interfaces

import org.json.JSONObject

interface ConfigExportImport {

    fun configuration(): JSONObject
    fun applyConfiguration(configuration: JSONObject)
}