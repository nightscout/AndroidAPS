package info.nightscout.androidaps.plugins.insulin

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Insulin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 14/08/17.
 */
@Singleton
class InsulinOrefRapidActingPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config
) : InsulinOrefBasePlugin(injector, rh, profileFunction, rxBus, aapsLogger, config) {

    override val id get(): Insulin.InsulinType = Insulin.InsulinType.OREF_RAPID_ACTING
    override val friendlyName get(): String = rh.gs(R.string.rapid_acting_oref)

    override fun configuration(): JSONObject = JSONObject()
    override fun applyConfiguration(configuration: JSONObject) {}

    override fun commentStandardText(): String = rh.gs(R.string.fastactinginsulincomment)

    override val peak = 75

    init {
        pluginDescription
            .pluginName(R.string.rapid_acting_oref)
            .description(R.string.description_insulin_rapid)
            .setDefault()
            .enableByDefault(true)
    }
}