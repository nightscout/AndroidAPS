package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.ui.compose.preference.InlineStringListPreferenceItem
import javax.inject.Inject

class SWRadioButton @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var labelsArray: Array<CharSequence> = emptyArray()
    private var valuesArray: Array<CharSequence> = emptyArray()

    fun option(labels: Array<CharSequence>, values: Array<CharSequence>): SWRadioButton {
        labelsArray = labels
        valuesArray = values
        return this
    }

    private fun labels(): Array<CharSequence> {
        return labelsArray
    }

    private fun values(): Array<CharSequence> {
        return valuesArray
    }

    fun preference(preference: StringPreferenceKey): SWRadioButton {
        this.preference = preference
        return this
    }

    @Composable
    override fun Compose() {
        val entries = LinkedHashMap<String, String>()
        for (i in valuesArray.indices) {
            entries[valuesArray[i].toString()] = labelsArray[i].toString()
        }
        InlineStringListPreferenceItem(
            stringKey = preference as StringPreferenceKey,
            titleResId = label ?: 0,
            entries = entries
        )
    }
}
