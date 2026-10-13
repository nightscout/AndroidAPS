package app.aaps.plugins.source

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference

abstract class AbstractBgSourcePlugin(
    pluginDescription: PluginDescription,
    ownPreferences: List<Class<out NonPreferenceKey>> = emptyList(),
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences
) : PluginBaseWithPreferences(pluginDescription, ownPreferences, aapsLogger, rh, preferences), BgSource {

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "bg_source_upload_settings"
            title = rh.gs(R.string.bgsource_settings)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.BgSourceUploadToNs, title = app.aaps.core.ui.R.string.do_ns_upload_title))
        }
    }
}
