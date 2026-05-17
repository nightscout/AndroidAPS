package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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

    /**
     * Manually supply labels + values. Only needed when the options are runtime-generated
     * (e.g. age ranges with localized labels). For static list preferences, leave this
     * unset — entries are resolved from [StringPreferenceKey.entries].
     */
    fun option(labels: Array<CharSequence>, values: Array<CharSequence>): SWRadioButton {
        labelsArray = labels
        valuesArray = values
        return this
    }

    fun preference(preference: StringPreferenceKey): SWRadioButton {
        this.preference = preference
        return this
    }

    @Composable
    override fun Compose() {
        val key = preference as StringPreferenceKey
        val entries = if (labelsArray.isNotEmpty()) {
            // Runtime-supplied options.
            LinkedHashMap<String, String>().apply {
                for (i in valuesArray.indices) {
                    put(valuesArray[i].toString(), labelsArray[i].toString())
                }
            }
        } else {
            // Static options declared on the StringPreferenceKey.
            key.entries.mapValues { (_, resId) -> stringResource(resId) }
        }
        InlineStringListPreferenceItem(
            stringKey = key,
            titleResId = label ?: 0,
            entries = entries
        )
    }
}
