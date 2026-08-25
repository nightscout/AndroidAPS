package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.AdaptiveDoublePreferenceItem
import javax.inject.Inject

class SWEditNumber @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var updateDelay = 0

    fun preference(preference: DoublePreferenceKey): SWEditNumber {
        this.preference = preference
        return this
    }

    fun updateDelay(updateDelay: Int): SWEditNumber {
        this.updateDelay = updateDelay
        return this
    }

    @Composable
    override fun Compose() {
        AdaptiveDoublePreferenceItem(
            doubleKey = preference as DoublePreferenceKey,
            titleResId = label ?: 0
        )
    }
}
