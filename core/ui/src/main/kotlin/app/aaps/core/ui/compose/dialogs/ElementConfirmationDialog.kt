package app.aaps.core.ui.compose.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId

/**
 * Shared confirmation dialog for the action dialogs (wizard, treatment, insulin, temp-basal, care, …).
 *
 * Carries the [elementType]'s visual identity — its label as the title and its themed icon/tint — so each
 * dialog only supplies the per-action [message] (a colored [AnnotatedString] summary) and the confirm/dismiss
 * callbacks, instead of re-inlining `OkCancelDialog(title = …labelResId(), icon = …icon(), iconTint = …color())`
 * in every screen.
 */
@Composable
fun ElementConfirmationDialog(
    elementType: ElementType,
    message: AnnotatedString,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    OkCancelDialog(
        title = stringResource(elementType.labelResId()),
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
    onDismiss: () -> Unit
) {
    ElementConfirmationDialog(
        elementType = elementType,
        message = lines.toAnnotatedString(primaryColor = elementType.color()),
        onConfirm = onConfirm,
        onDismiss = onDismiss
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
        title = stringResource(elementType.labelResId()),
        message = message,
        icon = elementType.icon(),
        iconTint = elementType.color(),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
