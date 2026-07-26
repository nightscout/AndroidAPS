package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.AdaptiveIntPreferenceItem
import javax.inject.Inject

class SWEditIntNumber @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var updateDelay = 0

    fun preference(preference: IntPreferenceKey): SWEditIntNumber {
        this.preference = preference
        return this
    }

    fun updateDelay(updateDelay: Int): SWEditIntNumber {
        this.updateDelay = updateDelay
        return this
    }

    @Composable
    override fun Compose() {
        AdaptiveIntPreferenceItem(
            intKey = preference as IntPreferenceKey,
            titleResId = label ?: 0
        )
    }
}
