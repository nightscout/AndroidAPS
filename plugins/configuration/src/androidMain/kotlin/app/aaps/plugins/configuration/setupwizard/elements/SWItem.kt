package app.aaps.plugins.configuration.setupwizard.elements

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

open class SWItem @Inject constructor(
    val aapsLogger: AAPSLogger,
    val rh: ResourceHelper,
    val rxBus: RxBus,
    val preferences: Preferences,
    val passwordCheck: PasswordCheck
) {

    // Lives as long as the item, like the Disposable before it, which only the next scheduleChange
    // ever cancelled. Default because the body only sends on the bus and touches no UI.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scheduledEventPost: Job? = null

    var label: TextRef? = null
    var comment: Int? = null
    var preference: PreferenceKey? = null

    open fun label(label: TextRef): SWItem {
        this.label = label
        return this
    }

    /** Convenience for the many call sites that pass their own module's `R.string.x`. */
    open fun label(@StringRes label: Int): SWItem = label(TextRef.AndroidRes(label))

    fun comment(@StringRes comment: Int): SWItem {
        this.comment = comment
        return this
    }

    open fun save(value: CharSequence, updateDelay: Long) {
        if (preference is StringNonPreferenceKey)
            preferences.put(preference as StringNonPreferenceKey, value.toString())
        if (preference is StringPreferenceKey)
            preferences.put(preference as StringPreferenceKey, value.toString())
        scheduleChange(updateDelay)
    }

    @Composable
    open fun Compose() {
    }

    fun scheduleChange(updateDelay: Long) {
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.cancel()
        scheduledEventPost = scope.launch {
            delay(updateDelay.seconds)
            rxBus.send(EventSWUpdate(false))
        }
    }
}