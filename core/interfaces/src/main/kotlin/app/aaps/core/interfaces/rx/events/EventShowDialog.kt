package app.aaps.core.interfaces.rx.events

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Request a modal dialog from anywhere in the app. Consumed by the root
 * [GlobalDialogHost] composable in `ComposeMainActivity`; the sealed variants
 * map 1:1 to the Compose dialogs under `core.ui.compose.dialogs`.
 *
 * Only one dialog renders at a time. Events that arrive while a dialog is
 * already on screen queue behind it.
 *
 * Callbacks fire on the UI thread (Compose button handlers), so UI-touching
 * work is safe inside them.
 */
sealed class EventShowDialog : Event() {

    /** Informational OK dialog. */
    data class Ok(
        val title: String,
        val message: String,
        val onOk: (() -> Unit)? = null
    ) : EventShowDialog()

    /**
     * Confirmation dialog with OK + Cancel.
     *
     * [message] is [CharSequence] so callers can pass either a plain [String]
     * (rendered via `AnnotatedString.fromHtml` — legacy HTML in string resources still works)
     * or a pre-built `androidx.compose.ui.text.AnnotatedString` to bypass HTML entirely
     * and style spans directly.
     */
    data class OkCancel(
        val title: String?,
        val message: CharSequence,
        val secondMessage: String? = null,
        val icon: ImageVector? = null,
        val onOk: () -> Unit,
        val onCancel: (() -> Unit)? = null
    ) : EventShowDialog()

    /** Three-option dialog: Yes / No / Cancel. */
    data class YesNoCancel(
        val title: String,
        val message: String,
        val onYes: () -> Unit,
        val onNo: (() -> Unit)? = null
    ) : EventShowDialog()

    /** Error dialog with warning icon + Dismiss + optional positive button. */
    data class Error(
        val title: String,
        val message: String,
        val positiveButton: String? = null,
        val onPositive: (() -> Unit)? = null,
        val onDismiss: (() -> Unit)? = null
    ) : EventShowDialog()
}
