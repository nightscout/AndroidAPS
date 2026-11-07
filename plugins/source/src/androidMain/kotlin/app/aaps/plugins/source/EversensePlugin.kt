package app.aaps.plugins.source

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcPluginEversense
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.source.eversense.EversenseComposeContent
import app.aaps.plugins.source.eversense.EversenseStringKey
import com.nightscout.eversense.EversenseCGMPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** AAPS 4 port of the Eversense integration scaffold from PR #191. */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(415)
@SingleIn(AppScope::class)
@Inject
class EversensePlugin(
    context: Context,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    notificationManager: NotificationManager
) : AbstractBgSourceWithSensorInsertLogPlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { EversenseComposeContent(rh) }
        .icon(IcPluginEversense)
        .pluginName(TextRef.AndroidRes(R.string.source_eversense))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.description_source_eversense)),
    ownPreferences = EversenseStringKey.entries,
    aapsLogger, rh, preferences, notificationManager
) {
    init {
        EversenseCGMPlugin.instance.setContext(context)
    }

    override fun getPreferenceScreenContent(): PreferenceSubScreenDef {
        val base = super.getPreferenceScreenContent()
        return base.copy(items = base.items + PreferenceSubScreenDef(
            key = "eversense_settings_glucose",
            titleResId = R.string.eversense_glucose_settings,
            items = listOf(
                PreferenceSubScreenDef(
                    key = "eversense_target_levels",
                    titleResId = R.string.eversense_target_levels,
                    items = listOf(EversenseStringKey.TargetHigh, EversenseStringKey.TargetLow)
                ),
                PreferenceSubScreenDef(
                    key = "eversense_alert_levels",
                    titleResId = R.string.eversense_alert_levels,
                    items = listOf(EversenseStringKey.AlertHigh, EversenseStringKey.AlertLow)
                )
            )
        ))
    }
}
