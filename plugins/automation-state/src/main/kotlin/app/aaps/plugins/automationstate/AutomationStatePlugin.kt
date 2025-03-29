package app.aaps.plugins.automationstate

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automationstate.ui.AutomationStateFragment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationStatePlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(AutomationStateFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_automation)
        .pluginName(R.string.automation_states)
        .shortName(R.string.automation_states_short)
        .description(R.string.description_automation_states)
        .enableByDefault(true)
        .visibleByDefault(true),
    aapsLogger, rh
) 