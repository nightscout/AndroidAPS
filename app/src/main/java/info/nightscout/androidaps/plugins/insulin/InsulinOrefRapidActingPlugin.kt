package info.nightscout.androidaps.plugins.insulin

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.InsulinInterface
import javax.inject.Inject

/**
 * Created by adrian on 14/08/17.
 */
class InsulinOrefRapidActingPlugin @Inject constructor(): InsulinOrefBasePlugin() {

    override fun getId(): Int {
        return InsulinInterface.OREF_RAPID_ACTING
    }

    override fun getFriendlyName(): String {
        return MainApp.gs(R.string.rapid_acting_oref)
    }

    override fun commentStandardText(): String {
        return MainApp.gs(R.string.fastactinginsulincomment)
    }

    override val peak = 75


    init {
        pluginDescription
            .pluginName(R.string.rapid_acting_oref)
            .description(R.string.description_insulin_rapid)
    }
}