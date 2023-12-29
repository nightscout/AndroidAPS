package app.aaps.core.data.plugin

/**
 * Main plugin type
 *
 * set by [app.aaps.core.interfaces.plugin.PluginDescription.mainType]
 */
enum class PluginType {

    GENERAL, SENSITIVITY, PROFILE, APS, PUMP, CONSTRAINTS, LOOP, BGSOURCE, INSULIN, SYNC, SMOOTHING
}