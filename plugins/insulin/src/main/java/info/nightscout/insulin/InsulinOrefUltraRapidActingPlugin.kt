package info.nightscout.insulin

import app.aaps.interfaces.configuration.Config
import app.aaps.interfaces.insulin.Insulin
import app.aaps.interfaces.logging.AAPSLogger
import app.aaps.interfaces.profile.ProfileFunction
import app.aaps.interfaces.resources.ResourceHelper
import app.aaps.interfaces.rx.bus.RxBus
import app.aaps.interfaces.ui.UiInteraction
import app.aaps.interfaces.utils.HardLimits
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 14/08/17.
 */
@Singleton
class InsulinOrefUltraRapidActingPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config,
    hardLimits: HardLimits,
    uiInteraction: UiInteraction
) : InsulinOrefBasePlugin(injector, rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction) {

    override val id get(): Insulin.InsulinType = Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING
    override val friendlyName get(): String = rh.gs(R.string.ultra_rapid_oref)

    override fun configuration(): JSONObject = JSONObject()
    override fun applyConfiguration(configuration: JSONObject) {}

    override fun commentStandardText(): String = rh.gs(R.string.ultra_fast_acting_insulin_comment)

    override val peak = 55

    init {
        pluginDescription
            .pluginName(R.string.ultra_rapid_oref)
            .description(R.string.description_insulin_ultra_rapid)
    }
}