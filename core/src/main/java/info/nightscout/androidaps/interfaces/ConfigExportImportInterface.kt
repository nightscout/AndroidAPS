package info.nightscout.androidaps.interfaces

import org.json.JSONObject

interface ConfigExportImportInterface {

    fun configuration(): JSONObject
    fun applyConfiguration(configuration: JSONObject)
}