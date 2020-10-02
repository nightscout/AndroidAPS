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

@Singleton
class InsulinLyumjevPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBusWrapper, aapsLogger: AAPSLogger
) : InsulinOrefBasePlugin(injector, resourceHelper, profileFunction, rxBus, aapsLogger) {


    override fun getId(): Int = InsulinInterface.OREF_LYUMJEV

    override fun getFriendlyName(): String = resourceHelper.gs(R.string.lyumjev)

    override fun commentStandardText(): String = resourceHelper.gs(R.string.lyumjev)

    override val peak = 45

    init {
        pluginDescription
            .pluginName(R.string.lyumjev)
            .description(R.string.description_insulin_lyumjev)
    }
}