package info.nightscout.androidaps.plugins.general.food

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(FoodFragment::class.java.name)
    .pluginIcon(R.drawable.ic_food)
    .pluginName(R.string.food)
    .shortName(R.string.food_short)
    .description(R.string.description_food),
    aapsLogger, resourceHelper, injector
) {

    var service: FoodService? = null

    override fun onStart() {
        super.onStart()
        service = FoodService(injector)
    }
}