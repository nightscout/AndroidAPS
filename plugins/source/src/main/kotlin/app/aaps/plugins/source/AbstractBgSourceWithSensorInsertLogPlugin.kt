package app.aaps.plugins.source

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef

abstract class AbstractBgSourceWithSensorInsertLogPlugin(
    pluginDescription: PluginDescription,
    ownPreferences: List<NonPreferenceKey> = emptyList(),
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
) : PluginBaseWithPreferences(pluginDescription, ownPreferences, aapsLogger, rh, preferences), BgSource {

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "bg_source_with_sensor_settings",
        // a BG source plugin always names itself
        title = pluginDescription.pluginName!!,
        items = listOf(
            BooleanKey.BgSourceUploadToNs,
            BooleanKey.BgSourceCreateSensorChange

        ),
        icon = pluginDescription.icon
    )
}
