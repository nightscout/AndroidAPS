package info.nightscout.plugins.general.actions

import app.aaps.interfaces.actions.Actions
import app.aaps.interfaces.configuration.Config
import app.aaps.interfaces.logging.AAPSLogger
import app.aaps.interfaces.plugin.PluginBase
import app.aaps.interfaces.plugin.PluginDescription
import app.aaps.interfaces.plugin.PluginType
import app.aaps.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionsPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(ActionsFragment::class.qualifiedName)
        .enableByDefault(config.APS || config.PUMPCONTROL)
        .visibleByDefault(config.APS || config.PUMPCONTROL)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_action)
        .pluginName(R.string.actions)
        .shortName(R.string.actions_shortname)
        .description(R.string.description_actions),
    aapsLogger, rh, injector
), Actions
