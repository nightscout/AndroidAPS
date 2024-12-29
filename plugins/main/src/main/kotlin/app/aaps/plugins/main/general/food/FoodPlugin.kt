package app.aaps.plugins.main.general.food

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.main.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(FoodFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_food)
        .pluginName(R.string.food)
        .shortName(R.string.food_short)
        .description(R.string.description_food),
    aapsLogger, rh
)