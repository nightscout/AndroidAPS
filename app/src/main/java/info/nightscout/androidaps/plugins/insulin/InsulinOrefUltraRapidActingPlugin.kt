package info.nightscout.androidaps.plugins.insulin

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 14/08/17.
 */
@Singleton
class InsulinOrefUltraRapidActingPlugin @Inject constructor(
    private val sp: SP,
    resourceHelper: ResourceHelper,
    rxBus: RxBusWrapper,
    profileFunction: ProfileFunction
) : InsulinOrefBasePlugin(rxBus, resourceHelper, profileFunction) {


    override fun getId(): Int {
        return InsulinInterface.OREF_ULTRA_RAPID_ACTING
    }

    override fun getFriendlyName(): String {
        return resourceHelper.gs(R.string.ultrarapid_oref)
    }

    override fun commentStandardText(): String {
        return resourceHelper.gs(R.string.ultrafastactinginsulincomment)
    }

    override val peak = 55

    init {
        pluginDescription
            .pluginName(R.string.ultrarapid_oref)
            .description(R.string.description_insulin_ultra_rapid)
    }
}