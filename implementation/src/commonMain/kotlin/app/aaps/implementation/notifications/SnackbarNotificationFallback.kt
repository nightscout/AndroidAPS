package app.aaps.implementation.notifications

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.ui.SnackbarHostPresence
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Turns an `EventShowSnackbar` into a system notification when no snackbar host is there to show
 * it, so a message sent while the UI is gone is not lost.
 *
 * `GlobalSnackbarHost` collects only while its lifecycle is at least STARTED and holds a
 * [SnackbarHostPresence] handle for exactly that time. So [snackbarHostPresence] answers the only
 * question this class has: did anybody see the message? While a host is up it wins and nothing is
 * posted; with no host, this is the last stop.
 *
 * Every shell has to call [start] - `MainApp` on Android, `AapsAppHost` on iOS, `Main` on desktop.
 */
@SingleIn(AppScope::class)
@Inject
class SnackbarNotificationFallback(
    private val rxBus: RxBus,
    private val notificationManager: NotificationManager,
    private val snackbarHostPresence: SnackbarHostPresence,
    // Plain CoroutineScope, not @ApplicationScope: that qualifier is javax and cannot appear in
    // commonMain. AppCoroutineBindings.unqualifiedAppScope binds the very same scope without it.
    private val appScope: CoroutineScope
) {

    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch {
            rxBus.toFlow(EventShowSnackbar::class).collect { event ->
                if (snackbarHostPresence.activeHosts.value > 0) return@collect
                notificationManager.post(
                    id = NotificationId.SNACKBAR_FALLBACK,
                    text = event.message,
                    // URGENT is reserved for pump/loop alarms that play alarm-stream sounds and
                    // wake users. Generic snackbar errors - "failed to save preference", etc. -
                    // route through NORMAL instead.
                    level = when (event.type) {
                        EventShowSnackbar.Type.Error   -> NotificationLevel.NORMAL
                        EventShowSnackbar.Type.Warning -> NotificationLevel.NORMAL
                        EventShowSnackbar.Type.Success -> NotificationLevel.INFO
                        EventShowSnackbar.Type.Info    -> NotificationLevel.INFO
                    },
                    validMinutes = 30
                )
            }
        }
    }
}
