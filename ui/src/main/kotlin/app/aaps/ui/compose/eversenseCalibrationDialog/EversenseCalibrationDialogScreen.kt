package app.aaps.ui.compose.eversenseCalibrationDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.bottomBarSafeArea
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.R
import java.text.DecimalFormat
import app.aaps.core.ui.R as CoreUiR

@Composable
fun EversenseCalibrationDialogScreen(
    viewModel: EversenseCalibrationDialogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                EversenseCalibrationDialogViewModel.SideEffect.CalibrationAccepted     -> onNavigateBack()
                is EversenseCalibrationDialogViewModel.SideEffect.CalibrationFailed -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    EversenseCalibrationDialogContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBgChange = viewModel::updateBg,
        onNavigateBack = onNavigateBack,
        onSubmitClick = viewModel::submit
    )
}

@Composable
private fun EversenseCalibrationDialogContent(
    uiState: EversenseCalibrationDialogUiState,
    snackbarHostState: SnackbarHostState,
    onBgChange: (Double) -> Unit,
    onNavigateBack: () -> Unit,
    onSubmitClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(ElementType.EVERSENSE_CALIBRATION.labelResId())) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(CoreUiR.string.close)
                        )
                    }
                },
                actions = {}
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSubmitClick()
                },
                enabled = uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .bottomBarSafeArea()
                    .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AapsSpacing.medium))
                if (uiState.hasValidBg) {
                    val bgFormat = remember(uiState.isMgdl) { if (uiState.isMgdl) DecimalFormat("0") else DecimalFormat("0.0") }
                    Text("${bgFormat.format(uiState.bg)} ${uiState.unitLabel}")
                } else {
                    Text(stringResource(CoreUiR.string.ok))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .clearFocusOnTap(focusManager)
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            if (uiState.notConnected) {
                NotConnectedCard()
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)) {
                    NumberInputRow(
                        labelResId = CoreUiR.string.bg_label,
                        value = uiState.bg,
                        onValueChange = onBgChange,
                        valueRange = uiState.bgRange,
                        step = uiState.bgStep,
                        unitLabel = uiState.unitLabel,
                        decimalPlaces = uiState.bgDecimalPlaces,
                        enabled = !uiState.submitting
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConnectedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(AapsSpacing.medium))
            Text(text = stringResource(R.string.eversense_calibration_not_connected), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
