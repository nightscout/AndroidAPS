package app.aaps.ui.compose.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.ui.compose.overview.chips.SensitivityChip
import app.aaps.ui.compose.overview.graphs.SensitivityUiState

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
            title = stringResource(app.aaps.core.ui.R.string.sensitivity),
            message = state.dialogText,
            onConfirm = { showSensitivityDialog = false },
            onDismiss = { showSensitivityDialog = false }
        )
    }
}
