package app.aaps.core.ui.compose.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.PasswordRequest
import app.aaps.core.ui.compose.stringResource

/**
 * Draws whatever [passwordCheck] is currently asking for.
 *
 * Place once, near the root of the UI, inside the app theme. Nothing else needs to know a prompt is
 * happening: a caller anywhere asks `passwordCheck.queryPassword(...)` and the dialog appears here.
 *
 * This replaces an `android.app.Dialog` wrapped around a `ComposeView` with three `setViewTree*Owner`
 * calls and a hand written lifecycle owner - all of which existed only to run Compose from
 * non-Compose code. Rendering in the composition that is already running needs none of it, and is
 * the reason [PasswordCheck] no longer takes a `Context`.
 */
@Composable
fun PasswordCheckHost(passwordCheck: PasswordCheck) {
    val request by passwordCheck.request.collectAsStateWithLifecycle()

    when (val current = request) {
        null                        -> Unit

        is PasswordRequest.Query    ->
            QueryPasswordDialog(
                title = stringResource(current.label),
                pinInput = current.pinInput,
                onConfirm = current.onConfirm,
                onCancel = current.onCancel
            )

        is PasswordRequest.Set      ->
            SetPasswordDialog(
                title = stringResource(current.label),
                pinInput = current.pinInput,
                onConfirm = current.onConfirm,
                onCancel = current.onCancel
            )

        is PasswordRequest.QueryAny ->
            QueryAnyPasswordDialog(
                title = stringResource(current.label),
                passwordExplanation = current.explanation?.let { stringResource(it) },
                passwordWarning = current.warning?.let { stringResource(it) },
                onConfirm = current.onConfirm,
                onCancel = current.onCancel
            )
    }
}
