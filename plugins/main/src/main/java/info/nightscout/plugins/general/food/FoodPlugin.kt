package info.nightscout.plugins.general.food

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.plugins.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
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
    .pluginIcon(info.nightscout.core.main.R.drawable.ic_food)
    .pluginName(R.string.food)
    .shortName(R.string.food_short)
    .description(R.string.description_food),
    aapsLogger, rh, injector
)