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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.SnackbarColors

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
 */
@Composable
fun GlobalSnackbarHost(
    rxBus: RxBus,
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.snackbarColors
    val lifecycleOwner = LocalLifecycleOwner.current

    // Scoped to STARTED so the collector is cancelled when the activity goes
    // to the background. The application-scope collector in MainApp then
    // takes over and routes events to a system Notification. Without this,
    // both collectors would fire during the ProcessLifecycle STARTED→CREATED
    // transition, double-surfacing messages.
    LaunchedEffect(rxBus, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            rxBus.toFlow(EventShowSnackbar::class.java).collect { event ->
                hostState.showSnackbar(
                    BusSnackbarVisuals(message = event.message, type = event.type)
                )
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

        Snackbar(
            containerColor = containerColor,
            contentColor = contentColor,
            dismissAction = {
                TextButton(onClick = { hostState.currentSnackbarData?.dismiss() }) {
                    Text(
                        text = stringResource(R.string.dismiss),
                        color = contentColor
                    )
                }
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
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
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

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
