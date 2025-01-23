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
}