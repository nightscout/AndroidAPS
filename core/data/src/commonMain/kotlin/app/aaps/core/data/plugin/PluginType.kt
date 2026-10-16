package app.aaps.core.data.plugin

/**
 * Main plugin type
 *
 * set by [app.aaps.core.interfaces.plugin.PluginDescription.mainType]
 */
enum class PluginType(
    /** One active plugin per category (radio) vs. multi-select (checkbox). */
    val singleSelect: Boolean = false,
    /** The active selection syncs master↔client (drives the ActivePlugin* keys + client visibility). */
    val selectionSyncs: Boolean = false
) {

    GENERAL,
    SENSITIVITY(singleSelect = true, selectionSyncs = true),
    APS(singleSelect = true, selectionSyncs = true),
    PUMP(singleSelect = true),
    CONSTRAINTS,
    LOOP,
    BGSOURCE(singleSelect = true),
    SYNC,
    SMOOTHING(singleSelect = true, selectionSyncs = true),
    CALIBRATION(singleSelect = true, selectionSyncs = true)
}