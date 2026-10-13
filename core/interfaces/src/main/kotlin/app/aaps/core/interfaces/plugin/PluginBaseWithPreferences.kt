package app.aaps.core.interfaces.plugin

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * Add preference handling to [PluginBase]
 */
abstract class PluginBaseWithPreferences(
    pluginDescription: PluginDescription,
    val ownPreferences: List<Class<out NonPreferenceKey>> = emptyList(),
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    val preferences: Preferences
) : PluginBase(pluginDescription, aapsLogger, rh) {

    init {
        ownPreferences.forEach { preferences.registerPreferences(it) }
    }

    /**
     * Called before importing preferences.
     * On Preferences import current content of shared preferences is cleared and then
     * new content is imported.
     *
     * This function is called BEFORE clearing and importing preferences to preserve some current values.
     * Needed content should be stored into plugin variables and then processed and restored
     * in [app.aaps.core.interfaces.plugin.PluginBaseWithPreferences.afterImport]
     *
     * App is restarted after import.
     *
     * See: [app.aaps.core.interfaces.maintenance.ImportExportPrefs.doImportSharedPreferences]
     */
    open fun beforeImport() {}

    /**
     * Called after importing preferences.
     * On Preferences import current content of shared preferences is cleared and then
     * new content is imported.
     *
     * This function is called AFTER clearing and importing preferences to process and restore
     * saved state from [app.aaps.core.interfaces.plugin.PluginBaseWithPreferences.beforeImport]
     *
     * App is restarted after import.
     *
     * See: [app.aaps.core.interfaces.maintenance.ImportExportPrefs.doImportSharedPreferences]
     */
    open fun afterImport() {}
}