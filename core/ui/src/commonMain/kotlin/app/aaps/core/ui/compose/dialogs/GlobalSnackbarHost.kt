package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.ui.SnackbarHostPresence
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.SnackbarColors
import app.aaps.core.ui.compose.stringResource

/**
 * Root-level snackbar host that subscribes to [EventShowSnackbar] on [rxBus]
 * and renders messages on a shared [SnackbarHostState].
 *
 * Place one of these at the root of every `Activity.setContent { }` — all
 * downstream code can `rxBus.send(EventShowSnackbar(...))` from any thread
 * without needing a Context, a `LocalSnackbarHostState`, or a direct
 * reference to the host.
 *
 * The [hostState] should be the same instance provided via
 * `LocalSnackbarHostState` so that in-tree composables wanting a local
 * snackbar (e.g. undo actions) share the single active host.
 *
 * While this host collects it holds a [snackbarHostPresence] handle. That is
 * how `SnackbarNotificationFallback` knows a message is being shown here and
 * must not also become a system notification. Every host must pass it, or a
 * message shown on screen would be duplicated in the notification shade.
 */
@Composable
fun GlobalSnackbarHost(
    rxBus: RxBus,
    snackbarHostPresence: SnackbarHostPresence,
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.snackbarColors
    val lifecycleOwner = LocalLifecycleOwner.current

    // Scoped to STARTED so the collector is cancelled when the activity goes
    // to the background. `SnackbarNotificationFallback` then takes over and
    // routes events to a system Notification. Without this, both collectors
    // would fire during the STARTED→CREATED transition, double-surfacing
    // messages.
    //
    // The presence handle is held for exactly as long as this collector runs,
    // which is what tells the fallback to stay out of the way. It is released
    // on cancellation too, so the hand-off cannot be missed.
    LaunchedEffect(rxBus, snackbarHostPresence, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snackbarHostPresence.acquire().use {
                rxBus.toFlow(EventShowSnackbar::class).collect { event ->
                    hostState.showSnackbar(
                        BusSnackbarVisuals(message = event.message, type = event.type)
                    )
                }
            }
        }
    }

    SnackbarHost(
        hostState = hostState,
        modifier = modifier.navigationBarsPadding()
    ) { snackbarData ->
        val visuals = snackbarData.visuals
        val type = (visuals as? BusSnackbarVisuals)?.type
        val (containerColor, contentColor, icon) = resolveBusStyle(type, colors)
        val severity = severityDescription(type)

        Snackbar(
            containerColor = containerColor,
            contentColor = contentColor,
            dismissAction = {
                TextButton(onClick = { hostState.currentSnackbarData?.dismiss() }) {
                    Text(
                        text = stringResource(CoreUiStrings.dismiss),
                        color = contentColor
                    )
                }
            }
        ) {
            // No liveRegion or paneTitle is added here on purpose. Material3's SnackbarHost
            // already wraps this content in a node that carries `liveRegion = Polite` and a
            // `paneTitle`, so the snackbar is announced when it appears. A second paneTitle on
            // a node inside it would send a second "pane appeared" event and the user would
            // hear the snackbar twice.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The icon is the only carrier of severity for a screen reader, because the
                // severity is otherwise shown by colour alone. It is a leaf node, so this
                // description is read next to the message, not instead of it.
                Icon(
                    imageVector = icon,
                    contentDescription = severity,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = visuals.message,
                    color = contentColor
                )
            }
        }
    }
}

private class BusSnackbarVisuals(
    override val message: String,
    val type: EventShowSnackbar.Type,
    override val withDismissAction: Boolean = true,
    override val actionLabel: String? = null
) : SnackbarVisuals {

    /**
     * Error and Warning stay on screen longer. They are the messages the user must not miss,
     * and a screen reader needs time to read them out before the snackbar disappears.
     * Info and Success keep the short default.
     */
    override val duration: SnackbarDuration =
        when (type) {
            EventShowSnackbar.Type.Error,
            EventShowSnackbar.Type.Warning -> SnackbarDuration.Long

            EventShowSnackbar.Type.Info,
            EventShowSnackbar.Type.Success -> SnackbarDuration.Short
        }
}

/** Short word naming how serious the message is, so it is not carried by colour alone. */
@Composable
private fun severityDescription(type: EventShowSnackbar.Type?): String =
    when (type) {
        EventShowSnackbar.Type.Error   -> stringResource(CoreUiStrings.error)
        EventShowSnackbar.Type.Warning -> stringResource(CoreUiStrings.warning)
        EventShowSnackbar.Type.Success -> stringResource(CoreUiStrings.success)
        EventShowSnackbar.Type.Info,
        null                           -> stringResource(CoreUiStrings.info)
    }

@Composable
private fun resolveBusStyle(
    type: EventShowSnackbar.Type?,
    colors: SnackbarColors
): Triple<Color, Color, ImageVector> =
    when (type) {
        EventShowSnackbar.Type.Error -> Triple(colors.errorContainer, colors.onErrorContainer, Icons.Default.Error)
        EventShowSnackbar.Type.Warning -> Triple(colors.warningContainer, colors.onWarningContainer, Icons.Default.Warning)
        EventShowSnackbar.Type.Info -> Triple(colors.infoContainer, colors.onInfoContainer, Icons.Default.Info)
        EventShowSnackbar.Type.Success -> Triple(colors.successContainer, colors.onSuccessContainer, Icons.Default.CheckCircle)
        null -> Triple(
            MaterialTheme.colorScheme.inverseSurface,
            MaterialTheme.colorScheme.inverseOnSurface,
            Icons.Default.Info
        )
    }
