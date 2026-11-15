package app.aaps.core.ui.compose.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResourceOrNull
import kotlinx.coroutines.delay
import kotlin.time.TimeMark

/**
 * Shared confirmation dialog for the action dialogs (wizard, treatment, insulin, temp-basal, care, …).
 *
 * Carries the [elementType]'s visual identity — its label as the title and its themed icon/tint — so each
 * dialog only supplies the per-action [message] (a colored [AnnotatedString] summary) and the confirm/dismiss
 * callbacks, instead of re-inlining `OkCancelDialog(title = …label(), icon = …icon(), iconTint = …color())`
 * in every screen.
 *
 * [closeAt], when set, dismisses the dialog once that time has passed: a bolus confirmation whose dose is only good
 * for a while (see [app.aaps.core.interfaces.queue.SmbPause]).
 */
@Composable
fun ElementConfirmationDialog(
    elementType: ElementType,
    message: AnnotatedString,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    closeAt: TimeMark? = null
) {
    if (closeAt != null) {
        val dismiss by rememberUpdatedState(onDismiss)
        LaunchedEffect(closeAt) {
            delay(-closeAt.elapsedNow())
            dismiss()
        }
    }
    OkCancelDialog(
        title = (stringResourceOrNull(elementType.label()) ?: ""),
        message = message,
        icon = elementType.icon(),
        iconTint = elementType.color(),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** Structured overload — the [lines] are tinted with theme colors here (render time). */
@Composable
fun ElementConfirmationDialog(
    elementType: ElementType,
    lines: List<ConfirmationLine>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    closeAt: TimeMark? = null
) {
    ElementConfirmationDialog(
        elementType = elementType,
        message = lines.toAnnotatedString(primaryColor = elementType.color()),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        closeAt = closeAt
    )
}

/** Plain-text overload, for confirmations whose summary isn't a colored [AnnotatedString] (yet). */
@Composable
fun ElementConfirmationDialog(
    elementType: ElementType,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    OkCancelDialog(
        title = (stringResourceOrNull(elementType.label()) ?: ""),
        message = message,
        icon = elementType.icon(),
        iconTint = elementType.color(),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
