package info.nightscout.insulin

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsulinLyumjevPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config,
    hardLimits: HardLimits,
    uiInteraction: UiInteraction
) : InsulinOrefBasePlugin(injector, rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction) {

    override val id get(): Insulin.InsulinType = Insulin.InsulinType.OREF_LYUMJEV
    override val friendlyName get(): String = rh.gs(R.string.lyumjev)

    override fun configuration(): JSONObject = JSONObject()
    override fun applyConfiguration(configuration: JSONObject) {}

    override fun commentStandardText(): String = rh.gs(R.string.lyumjev)

    override val peak = 45

    init {
        pluginDescription
            .pluginIcon(R.drawable.ic_insulin)
            .pluginName(R.string.lyumjev)
            .description(R.string.description_insulin_lyumjev)
    }
}