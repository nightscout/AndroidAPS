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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfrezzaDialogScreen(
    viewModel: AfrezzaDialogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenWizard: () -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is AfrezzaDialogViewModel.SideEffect.ShowMessage -> onShowMessage(effect.message)
                is AfrezzaDialogViewModel.SideEffect.DoseLogged -> onNavigateBack()
                is AfrezzaDialogViewModel.SideEffect.OpenWizard -> onOpenWizard()
            }
        }
    }

    // Step 2: Confirm dose
    if (uiState.showConfirmation) {
        OkCancelDialog(
            title = stringResource(R.string.afrezza_confirm_title),
            message = stringResource(R.string.afrezza_confirm_log, uiState.selectedCartridge!!),
            icon = ElementType.INSULIN.icon(),
            iconTint = ElementType.INSULIN.color(),
            onConfirm = { viewModel.confirmAndLog() },
            onDismiss = { viewModel.dismissConfirmation() }
        )
    }


    // Step 5: Open bolus calculator for carbs?
    if (uiState.showCarbPrompt) {
        OkCancelDialog(
            title = stringResource(R.string.afrezza_carb_prompt_title),
            message = stringResource(R.string.afrezza_carb_prompt_message),
            onConfirm = { viewModel.openWizard() },
            onDismiss = { viewModel.dismissCarbPrompt() }
        )
    }

    // Step 1: Cartridge selector (bottom sheet)
    ModalBottomSheet(
        onDismissRequest = onNavigateBack,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (!uiState.isConfigured) {
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.afrezza_select_dose),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (insulinLabel.isNotEmpty()) {
            Text(
                text = insulinLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CartridgeButton(units = 4, onClick = { onCartridgeSelected(4) }, enabled = !isLogging)
            CartridgeButton(units = 8, onClick = { onCartridgeSelected(8) }, enabled = !isLogging)
            CartridgeButton(units = 12, onClick = { onCartridgeSelected(12) }, enabled = !isLogging)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CartridgeButton(
    units: Int,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 100.dp, height = 80.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${units}U",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun AfrezzaNotConfiguredContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.afrezza_not_configured),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}


