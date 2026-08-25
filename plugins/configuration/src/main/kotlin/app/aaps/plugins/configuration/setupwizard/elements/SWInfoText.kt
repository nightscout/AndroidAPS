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

class SWInfoText @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textLabel: String? = null
    private var visibilityValidator: (() -> Boolean)? = null

    override fun label(label: Int): SWInfoText {
        this.label = label
        return this
    }

    fun label(newLabel: String): SWInfoText {
        textLabel = newLabel
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWInfoText {
        this.visibilityValidator = visibilityValidator
        return this
    }

    @Composable
    override fun Compose() {
        if (visibilityValidator?.invoke() == false) return
        val text = textLabel ?: label?.let { stringResource(it) } ?: return
        Text(text = text)
    }
}
