package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.aaps.core.ui.compose.dialogs.OkDialog

/** Where the application protection stands for the screen on show. */
enum class AppLockState {

    /** Not answered yet: the prompt is up, or it is about to be. */
    CHECKING,
    OPEN,
    REFUSED
}

/**
 * Hides [content] until the application protection is granted, so BG, IOB and the graphs cannot be read
 * behind the prompt, also not by a screen reader.
 *
 * [content] stays composed under the cover. The lock asks again each time the app comes back to the
 * front, and removing the content would lose the screen the user was on.
 *
 * A refusal is handled here and not on one screen: on any other screen nobody answered it, and the
 * user could go on using the app after pressing Cancel.
 *
 * Only what is drawn inside this gate is covered. Dialogs and sheets are windows of their own, so a
 * caller must not start them while it is locked.
 */
@Composable
fun AppLockGate(
    state: AppLockState,
    refusedMessage: String,
    onRefused: () -> Unit,
    content: @Composable () -> Unit
) {
    val open = state == AppLockState.OPEN
    Box(Modifier.fillMaxSize()) {
        Box(if (open) Modifier.fillMaxSize() else Modifier.fillMaxSize().clearAndSetSemantics {}) {
            content()
        }
        if (!open) Surface(Modifier.fillMaxSize()) {}
    }
    if (state == AppLockState.REFUSED) {
        OkDialog(title = "", message = refusedMessage, onDismiss = onRefused)
    }
}
