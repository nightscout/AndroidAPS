package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
import dev.zacsweers.metro.Inject

class SWHtmlLink @Inject constructor(aapsLogger: AAPSLogger, rh: TextResolver, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textLabel: String? = null
    private var visibilityValidator: (() -> Boolean)? = null

    override fun label(label: TextRef): SWHtmlLink {
        this.label = label
        return this
    }


    fun label(newLabel: String): SWHtmlLink {
        textLabel = newLabel
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWHtmlLink {
        this.visibilityValidator = visibilityValidator
        return this
    }

    @Composable
    override fun Compose() {
        if (visibilityValidator?.invoke() == false) return
        val text = textLabel ?: label?.let { stringResource(it) } ?: return
        val uriHandler = LocalUriHandler.current
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                try {
                    uriHandler.openUri(text)
                } catch (_: Exception) {
                    // ignore invalid URIs
                }
            }
        )
    }
}
