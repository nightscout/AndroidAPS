package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.ui.compose.preference.InlineStringPreferenceItem
import javax.inject.Inject

class SWEditString @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var validator: ((string: String) -> Boolean)? = null
    private var updateDelay = 0L

    fun preference(preference: StringPreferenceKey): SWEditString {
        this.preference = preference
        return this
    }

    fun validator(validator: (string: String) -> Boolean): SWEditString {
        this.validator = validator
        return this
    }

    fun updateDelay(updateDelay: Long): SWEditString {
        this.updateDelay = updateDelay
        return this
    }

    @Composable
    override fun Compose() {
        InlineStringPreferenceItem(
            stringKey = preference as StringPreferenceKey,
            titleResId = label ?: 0
        )
    }
}
