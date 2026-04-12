package app.aaps.ui.compose.loopSheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopActionBottomSheet(
    state: LoopActionUiState,
    onPerform: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var performing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.xxLarge)
                .padding(bottom = AapsSpacing.xxLarge)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(app.aaps.core.ui.R.string.tempbasal_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(AapsSpacing.large))
            Text(
                text = state.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AapsSpacing.medium))
            Text(
                text = state.request,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(AapsSpacing.xxLarge))
            Button(
                enabled = !performing,
                onClick = {
                    if (performing) return@Button
                    performing = true
                    onPerform()
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.loop_accept_perform))
            }
        }
    }
}
