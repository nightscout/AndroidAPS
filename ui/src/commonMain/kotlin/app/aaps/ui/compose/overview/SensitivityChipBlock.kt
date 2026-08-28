package app.aaps.ui.compose.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.ui.compose.overview.chips.SensitivityChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState

@Composable
fun SensitivityChipBlock(
    state: SensitivityUiState,
    modifier: Modifier = Modifier
) {
    if (state.asText.isEmpty() && state.isfFrom.isEmpty()) return

    var showSensitivityDialog by remember { mutableStateOf(false) }
    SensitivityChip(
        state = state,
        onClick = { if (state.dialogText.isNotEmpty()) showSensitivityDialog = true },
        modifier = modifier
    )
    if (showSensitivityDialog) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.sensitivity),
            message = state.dialogText,
            onConfirm = { showSensitivityDialog = false },
            onDismiss = { showSensitivityDialog = false }
        )
    }
}
