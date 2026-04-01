package app.aaps.core.interfaces.configuration

import kotlinx.serialization.json.JsonObject

/**
 * Allow export and import plugin configuration
 */
interface ConfigExportImport {

    /**
     *  Export configuration to JSON
     */
    fun configuration(): JsonObject

    /**
     * Import configuration from JSON and store it
     */
    fun applyConfiguration(configuration: JsonObject)
}