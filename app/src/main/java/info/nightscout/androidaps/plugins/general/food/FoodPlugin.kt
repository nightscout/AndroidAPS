package info.nightscout.androidaps.plugins.general.food

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodPlugin @Inject constructor() : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(FoodFragment::class.java.name)
    .pluginName(R.string.food)
    .shortName(R.string.food_short)
    .description(R.string.description_food)
) {

    var service: FoodService? = null

    override fun onStart() {
        super.onStart()
        service = FoodService()
    }
}