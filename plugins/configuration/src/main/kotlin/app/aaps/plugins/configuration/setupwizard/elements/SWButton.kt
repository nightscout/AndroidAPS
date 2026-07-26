package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import javax.inject.Inject

class SWButton @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var buttonRunnable: Runnable? = null
    private var buttonText = 0
    private var buttonValidator: (() -> Boolean)? = null

    fun text(buttonText: Int): SWButton {
        this.buttonText = buttonText
        return this
    }

    fun action(buttonRunnable: Runnable): SWButton {
        this.buttonRunnable = buttonRunnable
        return this
    }

    fun visibility(buttonValidator: () -> Boolean): SWButton {
        this.buttonValidator = buttonValidator
        return this
    }

    @Composable
    override fun Compose() {
        val enabled = buttonValidator?.invoke() != false
        androidx.compose.material3.Button(
            onClick = { buttonRunnable?.run() },
            enabled = enabled
        ) {
            Text(text = stringResource(buttonText))
        }
    }
}
