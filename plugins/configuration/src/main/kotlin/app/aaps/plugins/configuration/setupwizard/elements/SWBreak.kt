package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsSpacing
import javax.inject.Inject

class SWBreak @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var visibilityValidator: (() -> Boolean)? = null

    fun visibility(visibilityValidator: () -> Boolean): SWBreak {
        this.visibilityValidator = visibilityValidator
        return this
    }

    @Composable
    override fun Compose() {
        if (visibilityValidator?.invoke() == false) return
        Spacer(modifier = Modifier.height(AapsSpacing.medium))
    }
}
