package info.nightscout.interfaces

import org.json.JSONObject

/**
 * Allow export and import plugin configuration
 */
interface ConfigExportImport {

    /**
     *  Export configuration to JSON
     */
    fun configuration(): JSONObject

    /**
     * Import configuration from JSON and store it
     */
    fun applyConfiguration(configuration: JSONObject)
}