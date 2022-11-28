package info.nightscout.plugins.insulin

import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.putInt
import info.nightscout.core.extensions.storeInt
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.plugins.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 14/08/17.
 */
@Singleton
class InsulinOrefFreePeakPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config,
    hardLimits: HardLimits
) : InsulinOrefBasePlugin(injector, rh, profileFunction, rxBus, aapsLogger, config, hardLimits) {

    override val id get(): Insulin.InsulinType = Insulin.InsulinType.OREF_FREE_PEAK

    override val friendlyName get(): String = rh.gs(R.string.free_peak_oref)

    override fun configuration(): JSONObject = JSONObject().putInt(R.string.key_insulin_oref_peak, sp, rh)
    override fun applyConfiguration(configuration: JSONObject) {
        configuration.storeInt(R.string.key_insulin_oref_peak, sp, rh)
    }

    override fun commentStandardText(): String {
        return rh.gs(R.string.insulin_peak_time) + ": " + peak
    }

    override val peak: Int
        get() = sp.getInt(R.string.key_insulin_oref_peak, DEFAULT_PEAK)

    companion object {

        private const val DEFAULT_PEAK = 75
    }

    init {
        pluginDescription
            .pluginIcon(R.drawable.ic_insulin)
            .pluginName(R.string.free_peak_oref)
            .preferencesId(R.xml.pref_insulinoreffreepeak)
            .description(R.string.description_insulin_free_peak)
    }
}