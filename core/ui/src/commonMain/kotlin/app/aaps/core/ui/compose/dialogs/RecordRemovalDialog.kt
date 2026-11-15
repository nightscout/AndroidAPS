package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.clientcontrol.RecordRemoval
import app.aaps.core.ui.compose.stringResource

/**
 * Shows the master's description of a prepared [RecordRemoval] and confirms or cancels it. Renders nothing while no
 * removal is waiting. [onApplied] runs once the master removed the records.
 */
@Composable
fun RecordRemovalDialog(
    removal: RecordRemoval,
    onApplied: () -> Unit
) {
    val confirmation by removal.confirmation.collectAsStateWithLifecycle()
    confirmation?.let { pending ->
        OkCancelDialog(
            title = stringResource(CoreUiStrings.removerecord),
            message = pending.lines.toAnnotatedString(primaryColor = MaterialTheme.colorScheme.error),
            onConfirm = { removal.confirm(onApplied) },
            onDismiss = { removal.dismiss() }
        )
    }
}
