package info.nightscout.androidaps.plugins.insulin

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.utils.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 14/08/17.
 */
@Singleton
class InsulinOrefFreePeakPlugin @Inject constructor(
    private val sp: SP,
    private val resourceHelper: ResourceHelper
) : InsulinOrefBasePlugin() {

    override fun getId(): Int {
        return InsulinInterface.OREF_FREE_PEAK
    }

    override fun getFriendlyName(): String {
        return resourceHelper.gs(R.string.free_peak_oref)
    }

    public override fun commentStandardText(): String {
        return resourceHelper.gs(R.string.insulin_peak_time) + ": " + peak
    }

    public override fun getPeak(): Int {
        return sp.getInt(R.string.key_insulin_oref_peak, DEFAULT_PEAK)
    }

    companion object {
        private const val DEFAULT_PEAK = 75
    }

    init {
        pluginDescription
            .pluginName(R.string.free_peak_oref)
            .preferencesId(R.xml.pref_insulinoreffreepeak)
            .description(R.string.description_insulin_free_peak)
    }
}