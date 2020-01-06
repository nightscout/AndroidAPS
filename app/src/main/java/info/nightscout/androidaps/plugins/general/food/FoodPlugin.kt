package info.nightscout.androidaps.plugins.general.food

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodPlugin @Inject constructor(rxBus: RxBusWrapper, aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(FoodFragment::class.java.name)
    .pluginName(R.string.food)
    .shortName(R.string.food_short)
    .description(R.string.description_food),
    rxBus, aapsLogger
) {

    var service: FoodService? = null

    override fun onStart() {
        super.onStart()
        service = FoodService()
    }
}