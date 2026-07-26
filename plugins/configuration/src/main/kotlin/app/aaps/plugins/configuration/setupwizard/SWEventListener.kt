package app.aaps.plugins.configuration.setupwizard

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventStatus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import javax.inject.Inject

class SWEventListener @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    rxBus: RxBus,
    preferences: Preferences,
    passwordCheck: PasswordCheck
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textLabel = 0
    private var status = ""
    private var visibilityValidator: (() -> Boolean)? = null

    lateinit var clazz: Class<out EventStatus>

    fun with(clazz: Class<out EventStatus>): SWEventListener {
        this.clazz = clazz
        return this
    }

    override fun label(label: Int): SWEventListener {
        textLabel = label
        return this
    }

    fun initialStatus(status: String): SWEventListener {
        this.status = status
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWEventListener {
        this.visibilityValidator = visibilityValidator
        return this
    }

    @Composable
    override fun Compose() {
        if (visibilityValidator?.invoke() == false) return
        val context = LocalContext.current
        val statusState = remember { mutableStateOf(status) }
        DisposableEffect(clazz) {
            val disposable = rxBus
                .toObservable(clazz)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { event ->
                    statusState.value = event.getStatus(context)
                }
            onDispose { disposable.dispose() }
        }
        val labelText = if (textLabel != 0) stringResource(textLabel) else ""
        Text(text = "$labelText ${statusState.value}".trim())
    }
}
