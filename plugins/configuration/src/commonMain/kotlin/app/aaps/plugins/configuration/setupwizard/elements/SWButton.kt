package app.aaps.plugins.configuration.setupwizard.elements

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.keys.interfaces.TextRef
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.Inject

class SWButton @Inject constructor(aapsLogger: AAPSLogger, rh: TextResolver, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var buttonRunnable: (() -> Unit)? = null
    private var buttonText: TextRef? = null
    private var buttonValidator: (() -> Boolean)? = null

    fun text(buttonText: TextRef): SWButton {
        this.buttonText = buttonText
        return this
    }

    fun action(buttonRunnable: () -> Unit): SWButton {
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
            onClick = { buttonRunnable?.invoke() },
            enabled = enabled
        ) {
            buttonText?.let { Text(text = stringResource(it)) }
        }
    }
}
