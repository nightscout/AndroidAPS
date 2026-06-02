package app.aaps.ui.compose.afrezzaDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfrezzaDialogScreen(
    viewModel: AfrezzaDialogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is AfrezzaDialogViewModel.SideEffect.ShowMessage -> onShowMessage(effect.message)
                is AfrezzaDialogViewModel.SideEffect.DoseLogged -> onNavigateBack()
            }
        }
    }

    // Confirmation dialog
    if (uiState.showConfirmation && uiState.selectedCartridge != null) {
        OkCancelDialog(
            title = stringResource(R.string.log_afrezza_dose),
            message = stringResource(R.string.afrezza_confirm_log, uiState.selectedCartridge!!),
            icon = ElementType.INSULIN.icon(),
            iconTint = ElementType.INSULIN.color(),
            onConfirm = { viewModel.confirmAndLog() },
            onDismiss = { viewModel.dismissConfirmation() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onNavigateBack,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (!uiState.isConfigured) {
            // Afrezza not yet added in Insulin Management
            AfrezzaNotConfiguredContent()
        } else {
            AfrezzaCartridgeSelector(
                onCartridgeSelected = { units -> viewModel.selectCartridge(units) },
                isLogging = uiState.isLogging,
                insulinLabel = uiState.afrezzaIcfg?.insulinLabel ?: ""
            )
        }
    }
}

@Composable
private fun AfrezzaCartridgeSelector(
    onCartridgeSelected: (Int) -> Unit,
    isLogging: Boolean,
    insulinLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.log_afrezza_dose),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = insulinLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            CartridgeButton(units = 4, onClick = { onCartridgeSelected(4) }, enabled = !isLogging)
            CartridgeButton(units = 8, onClick = { onCartridgeSelected(8) }, enabled = !isLogging)
            CartridgeButton(units = 12, onClick = { onCartridgeSelected(12) }, enabled = !isLogging)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CartridgeButton(
    units: Int,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$units",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "units",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AfrezzaNotConfiguredContent() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = stringResource(R.string.afrezza_not_configured),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AfrezzaCartridgeSelectorPreview() {
    MaterialTheme {
        AfrezzaCartridgeSelector(
            onCartridgeSelected = {},
            isLogging = false,
            insulinLabel = "Afrezza (Inhaled) 40m 2.5h U100"
        )
    }
}
