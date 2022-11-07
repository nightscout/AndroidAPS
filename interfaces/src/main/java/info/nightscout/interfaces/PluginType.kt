package info.nightscout.interfaces

/**
 * Main plugin type
 *
 * set by [info.nightscout.androidaps.interfaces.PluginDescription.mainType]
 */
enum class PluginType {
    GENERAL, SENSITIVITY, PROFILE, APS, PUMP, CONSTRAINTS, LOOP, BGSOURCE, INSULIN, SYNC
}