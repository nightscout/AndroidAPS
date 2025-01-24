package app.aaps.plugins.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 14/08/17.
 */
@Singleton
class InsulinOrefRapidActingPlugin @Inject constructor(
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config,
    hardLimits: HardLimits,
    uiInteraction: UiInteraction
) : InsulinOrefBasePlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction) {

    override val id get(): Insulin.InsulinType = Insulin.InsulinType.OREF_RAPID_ACTING
    override val friendlyName get(): String = rh.gs(app.aaps.core.interfaces.R.string.rapid_acting_oref)
    override fun setDefault(iCfg: ICfg?) { }
    override fun getOrCreateInsulin(iCfg: ICfg) = ICfg("Rapid Acting Oref", dia, peak)

    override fun getInsulin(insulinLabel: String)= ICfg("Rapid Acting Oref", dia, peak)

    override fun configuration(): JSONObject = JSONObject()
    override fun applyConfiguration(configuration: JSONObject) {}

    override fun commentStandardText(): String = rh.gs(R.string.fast_acting_insulin_comment)

    override val peak = 75

    init {
        pluginDescription
            .pluginName(app.aaps.core.interfaces.R.string.rapid_acting_oref)
            .description(R.string.description_insulin_rapid)
            .setDefault()
            .enableByDefault(true)
    }
}