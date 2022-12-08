package info.nightscout.plugins.general.actions

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.actions.Actions
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.plugins.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
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
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_action)
        .pluginName(R.string.actions)
        .shortName(R.string.actions_shortname)
        .description(R.string.description_actions),
    aapsLogger, rh, injector
), Actions
