package app.aaps.ui.activities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.compose.stringResource
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

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
 * The title, the text and the action label come from the live alert in [Alerts.active], not from arguments, so an
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
fun AlertScreen(
    alerts: Alerts,
    source: String,
    key: String,
    uel: UserEntryLogger,
    onClose: () -> Unit,
    badge: (@Composable () -> Unit)? = null
) {
    val active by alerts.active.collectAsState()
    val alert = active.firstOrNull { it.source == source && it.key == key }

    LaunchedEffect(source, key) {
        alerts.active.first { list -> list.none { it.source == source && it.key == key } }
        onClose()
    }

    // "On every phone", ticked or not; a new alarm on the same screen starts unticked
    var everywhere by rememberSaveable(source, key) { mutableStateOf(false) }

    // The alert ended; the effect above is closing the host. Drawing a card with no content would only flash.
    if (alert == null) return

    // "Silence for 30 min" is a promise, and only an alert that lasts keeps it: one that ends when it is answered
    // (most of the app's own) gets plain Silence rather than a time it will not come back after.
    val lasts = alert.lifetime == AlertLifetime.CONDITION
    val canEverywhere = lasts && alerts.canSilenceEverywhere(alert)
    val silence: (Int) -> Unit = { minutes ->
        if (canEverywhere && everywhere) alerts.silenceEverywhere(source, key, minutes) else alerts.silence(source, key, minutes)
    }

    ErrorScreen(
        title = stringResource(alert.title),
        status = alert.text?.let { stringResource(it) } ?: "",
        onOk = {
            uel.log(Action.ERROR_DIALOG_OK, Sources.Unknown)
            alerts.silence(source, key)
            onClose()
        },
        onMute = {
            uel.log(Action.ERROR_DIALOG_MUTE, Sources.Unknown)
            alerts.silence(source, key)
        },
        onMute5Min = {
            uel.log(Action.ERROR_DIALOG_MUTE_5MIN, Sources.Unknown)
            alerts.snooze(source, key, UiInteraction.MUTE_MINUTES)
        },
        // Not offered when the alert's own action is already the delay, which is the "time to eat" timer.
        showMute5Min = alert.canMute,
        actionLabel = alert.action?.let { stringResource(it.label) },
        // The screen stays until the alert ends, so a failure of the action shows here.
        onAction = { alerts.act(source, key) },
        // One tap: silenced for that long, and closed (decided 2026-11-26)
        silenceLengths = if (lasts) Alerts.silenceLengths(alert.kind) else null,
        onSilenceFor = { minutes ->
            uel.log(Action.ERROR_DIALOG_OK, Sources.Unknown)
            silence(minutes)
            onClose()
        },
        silenceEverywhere = if (canEverywhere) everywhere else null,
        onSilenceEverywhere = { everywhere = it },
        // All the BG alarms for a while, from the one that rings: a pause of this phone, never urgent low. With "On every
        // phone" this alarm is silenced on the others too; a pause stays this phone's own.
        onSilenceAll = if (lasts && alert.kind is BgAlert && !alert.kind.isProtected) {
            {
                uel.log(Action.ERROR_DIALOG_OK, Sources.Unknown)
                if (canEverywhere && everywhere) alerts.silenceEverywhere(source, key, Alerts.SILENCE_ALL_MINUTES)
                alerts.pause(BgAlert.PAUSABLE, Clock.System.now().toEpochMilliseconds() + Alerts.SILENCE_ALL_MINUTES * 60_000L)
                onClose()
            }
        } else null,
        silenceAllMinutes = Alerts.SILENCE_ALL_MINUTES,
        // The moment it rings is when the user wants to say "not this again". Not for a protected kind, which always
        // rings. It also answers this alarm, as Silence does, so setting the kind back to Ring later does not ring it
        // again: a pump's alert stays as a card with no sound, one of the app's own is over.
        onSilenceKind = if (alert.kind.isProtected) null else {
            {
                alerts.silence(source, key)
                alerts.setChoice(alert.kind, AlertChoice.SILENT)
                onClose()
            }
        },
        badge = badge
    )
}
