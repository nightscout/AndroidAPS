package info.nightscout.plugins.general.food

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(FoodFragment::class.java.name)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_food)
        .pluginName(R.string.food)
        .shortName(R.string.food_short)
        .description(R.string.description_food),
    aapsLogger, rh, injector
)