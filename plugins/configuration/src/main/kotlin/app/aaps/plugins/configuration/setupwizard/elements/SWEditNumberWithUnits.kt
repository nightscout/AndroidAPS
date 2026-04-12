package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.ui.compose.preference.AdaptiveUnitDoublePreferenceItem
import javax.inject.Inject

class SWEditNumberWithUnits @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck, private val profileUtil: ProfileUtil) :
    SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var updateDelay = 0

    fun preference(preference: UnitDoublePreferenceKey): SWEditNumberWithUnits {
        this.preference = preference
        return this
    }

    fun updateDelay(updateDelay: Int): SWEditNumberWithUnits {
        this.updateDelay = updateDelay
        return this
    }

    @Composable
    override fun Compose() {
        AdaptiveUnitDoublePreferenceItem(
            unitKey = preference as UnitDoublePreferenceKey,
            titleResId = label ?: 0
        )
    }
}
