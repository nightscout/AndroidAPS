package info.nightscout.androidaps.plugins.insulin

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 14/08/17.
 */
@Singleton
class InsulinOrefUltraRapidActingPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBusWrapper, aapsLogger: AAPSLogger
) : InsulinOrefBasePlugin(injector, resourceHelper, profileFunction, rxBus, aapsLogger) {


    override fun getId(): Int = InsulinInterface.OREF_ULTRA_RAPID_ACTING

    override fun getFriendlyName(): String = resourceHelper.gs(R.string.ultrarapid_oref)

    override fun commentStandardText(): String = resourceHelper.gs(R.string.ultrafastactinginsulincomment)

    override val peak = 55

    init {
        pluginDescription
            .pluginName(R.string.ultrarapid_oref)
            .description(R.string.description_insulin_ultra_rapid)
    }
}