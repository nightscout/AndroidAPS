package app.aaps.plugins.configuration.setupwizard

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventStatus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
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

    private var textLabel: TextRef? = null
    private var status = ""
    private var visibilityValidator: (() -> Boolean)? = null

    lateinit var clazz: Class<out EventStatus>

    fun with(clazz: Class<out EventStatus>): SWEventListener {
        this.clazz = clazz
        return this
    }

    override fun label(label: TextRef): SWEventListener {
        textLabel = label
        return this
    }

    override fun label(label: Int): SWEventListener = label(TextRef.AndroidRes(label))

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
        // The event carries a TextRef now, so it is held unresolved and turned into text here, in
        // the Composable. That keeps the resolving out of the Rx callback, which had to reach for a
        // Context purely to read a string.
        val statusState = remember { mutableStateOf<TextRef>(TextRef.Literal(status)) }
        DisposableEffect(clazz) {
            val disposable = rxBus
                .toObservable(clazz)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { event ->
                    statusState.value = event.getStatus()
                }
            onDispose { disposable.dispose() }
        }
        val labelText = textLabel?.let { stringResource(it) } ?: ""
        Text(text = "$labelText ${stringResource(statusState.value)}".trim())
    }
}
