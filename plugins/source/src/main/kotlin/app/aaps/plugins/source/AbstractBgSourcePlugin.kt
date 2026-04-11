package app.aaps.plugins.source

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef

abstract class AbstractBgSourcePlugin(
    pluginDescription: PluginDescription,
    ownPreferences: List<Class<out NonPreferenceKey>> = emptyList(),
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    protected val config: Config
) : PluginBaseWithPreferences(pluginDescription, ownPreferences, aapsLogger, rh, preferences), BgSource {

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "bg_source_settings",
        titleResId = pluginDescription.pluginName,
        items = listOf(
            BooleanKey.BgSourceUploadToNs

        ),
        icon = pluginDescription.icon
    )
}
