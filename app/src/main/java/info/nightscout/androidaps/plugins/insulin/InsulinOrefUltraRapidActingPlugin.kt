package info.nightscout.androidaps.plugins.insulin

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.InsulinInterface
import javax.inject.Inject

/**
 * Created by adrian on 14/08/17.
 */
class InsulinOrefUltraRapidActingPlugin @Inject constructor(): InsulinOrefBasePlugin() {

    override fun getId(): Int {
        return InsulinInterface.OREF_ULTRA_RAPID_ACTING
    }

    override fun getFriendlyName(): String {
        return MainApp.gs(R.string.ultrarapid_oref)
    }

    override fun commentStandardText(): String {
        return MainApp.gs(R.string.ultrafastactinginsulincomment)
    }

    override val peak = 55


    init {
        pluginDescription
            .pluginName(R.string.ultrarapid_oref)
            .description(R.string.description_insulin_ultra_rapid)
    }
}