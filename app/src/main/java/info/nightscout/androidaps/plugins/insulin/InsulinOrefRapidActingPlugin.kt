package info.nightscout.androidaps.plugins.insulin

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

/**
 * Created by adrian on 14/08/17.
 */
class InsulinOrefRapidActingPlugin @Inject constructor(
    resourceHelper: ResourceHelper,
    rxBus: RxBusWrapper,
    profileFunction: ProfileFunction
) : InsulinOrefBasePlugin(rxBus, resourceHelper, profileFunction) {


    override fun getId(): Int {
        return InsulinInterface.OREF_RAPID_ACTING
    }

    override fun getFriendlyName(): String {
        return resourceHelper.gs(R.string.rapid_acting_oref)
    }

    override fun commentStandardText(): String {
        return resourceHelper.gs(R.string.fastactinginsulincomment)
    }

    override val peak = 75

    init {
        pluginDescription
            .pluginName(R.string.rapid_acting_oref)
            .description(R.string.description_insulin_rapid)
    }
}