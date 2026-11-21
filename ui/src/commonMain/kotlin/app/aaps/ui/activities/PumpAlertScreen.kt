package app.aaps.ui.activities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.alerts.PumpAlertLifetime
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.compose.stringResource
import kotlinx.coroutines.flow.first

/**
 * The alarm screen of one pump alert, for every pump driver and every platform.
 *
 * It is [ErrorScreen] with the pump alert wiring in front of it, and it is shared because both platforms need the
 * same screen: `ErrorActivity` hosts it on Android, the Compose host of the iOS app shows it there. The buttons are
 * the same on both, so a driver's alert looks and behaves the same wherever it is raised. See `_docs/PUMP_DESIGN.md`,
 * section 2.
 *
 * ## What it reads and what it does not
 *
 * The title, the text and the action label come from the live alert in [PumpAlerts.active], not from arguments, so an
 * alert the driver raises again with a new text - the same `key`, the same occurrence - shows the new text while the
 * screen is open. When the alert leaves `active`, because the pump dropped it or its action cleared it, there is
 * nothing left to show and [onClose] is called.
 *
 * This screen plays no sound. The notification list owns it, for an alert of a pump and for one of the app's own
 * alike: a second owner would mean two alarms for one condition, and a silence that stops only one of them.
 *
 * @param onClose the host closes itself - `finish()` on Android, dropping the overlay on iOS. Called both when the
 *   user chooses silence-and-close and when the alert ends on its own.
 */
@Composable
fun PumpAlertScreen(
    pumpAlerts: PumpAlerts,
    source: String,
    key: String,
    uel: UserEntryLogger,
    onClose: () -> Unit,
    badge: (@Composable () -> Unit)? = null
) {
    val active by pumpAlerts.active.collectAsState()
    val alert = active.firstOrNull { it.source == source && it.key == key }

    LaunchedEffect(source, key) {
        pumpAlerts.active.first { list -> list.none { it.source == source && it.key == key } }
        onClose()
    }

    // The alert ended; the effect above is closing the host. Drawing a card with no content would only flash.
    if (alert == null) return

    ErrorScreen(
        title = stringResource(alert.title),
        status = alert.text?.let { stringResource(it) } ?: "",
        onOk = {
            uel.log(Action.ERROR_DIALOG_OK, Sources.Unknown)
            pumpAlerts.silence(source, key)
            onClose()
        },
        onMute = {
            uel.log(Action.ERROR_DIALOG_MUTE, Sources.Unknown)
            pumpAlerts.silence(source, key)
        },
        onMute5Min = {
            uel.log(Action.ERROR_DIALOG_MUTE_5MIN, Sources.Unknown)
            pumpAlerts.snooze(source, key, UiInteraction.MUTE_MINUTES)
        },
        // Not offered when the alert's own action is already the delay, which is the "time to eat" timer.
        showMute5Min = alert.canMute,
        actionLabel = alert.action?.let { stringResource(it.label) },
        // The screen stays until the alert ends, so a failure of the action shows here.
        onAction = { pumpAlerts.act(source, key) },
        // "Silence for 30 min" is a promise, and only a pump's alert keeps it: one of the app's own is over when
        // it is answered, so its buttons say plain Silence rather than naming a time it will not come back after.
        silenceMinutes = if (alert.lifetime == PumpAlertLifetime.CONDITION) PumpAlerts.SILENCE_MINUTES else null,
        badge = badge
    )
}
