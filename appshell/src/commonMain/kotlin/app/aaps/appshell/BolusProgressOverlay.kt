package app.aaps.appshell

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.pump.PumpActivityDialog

/**
 * The modal bolus progress dialog, shown above every screen.
 *
 * It lives in the shared root rather than in a shell because all three shells need it: a client
 * mirrors the master's bolus through `BolusProgressData`, and while this was written inside
 * `ComposeMainActivity` only Android drew it - on iOS and on the desktop a relayed bolus ran to
 * completion with no progress shown at all.
 *
 * Only a standard bolus gets the modal. An SMB is not the user's own action, so it must not take
 * over the screen; the overview shows it in the pump FAB instead. Nothing is drawn when no bolus
 * is running.
 */
@Composable
internal fun BolusProgressOverlay(
    bolusState: BolusProgressState?,
    pumpStatus: String,
    queueStatus: AnnotatedString?,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = bolusState ?: return
    if (state.isSMB) return
    PumpActivityDialog(
        bolusState = state,
        pumpStatus = pumpStatus,
        queueStatus = queueStatus,
        isModal = true,
        onStop = onStop,
        onDismiss = onDismiss
    )
}
