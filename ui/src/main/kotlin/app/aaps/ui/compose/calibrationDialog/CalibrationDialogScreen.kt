package app.aaps.ui.compose.calibrationDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.bottomBarSafeArea
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.ElementConfirmationDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.R
import java.text.DecimalFormat
import app.aaps.core.ui.R as CoreUiR

@Composable
fun CalibrationDialogScreen(
    viewModel: CalibrationDialogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                CalibrationDialogViewModel.SideEffect.EntryAccepted -> onNavigateBack()
                is CalibrationDialogViewModel.SideEffect.EntryRejected -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    if (showConfirmation) {
        if (!viewModel.hasAction()) {
            showConfirmation = false
            showNoAction = true
        } else {
            ElementConfirmationDialog(
                elementType = ElementType.CALIBRATION,
                lines = viewModel.buildConfirmationSummary(),
                onConfirm = {
                    viewModel.confirmAndSave()
                    showConfirmation = false
                },
                onDismiss = { showConfirmation = false }
            )
        }
    }

    if (showNoAction) {
        ElementConfirmationDialog(
            elementType = ElementType.CALIBRATION,
            message = stringResource(CoreUiR.string.no_action_selected),
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    val preflightMessage = uiState.blockingPreconditions?.let(viewModel::preconditionMessage)

    CalibrationDialogContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        preflightMessage = preflightMessage,
        showMarkSensorChange = uiState.blockingPreconditions is AddEntryResult.Rejected.NoSession,
        canMarkSensorChange = uiState.canMarkSensorChange,
        onMarkSensorChange = viewModel::markSensorChangeNow,
        onBgChange = viewModel::updateBg,
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true }
    )
}

@Composable
private fun CalibrationDialogContent(
    uiState: CalibrationDialogUiState,
    snackbarHostState: SnackbarHostState,
    preflightMessage: String?,
    showMarkSensorChange: Boolean,
    canMarkSensorChange: Boolean,
    onMarkSensorChange: () -> Unit,
    onBgChange: (Double) -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(ElementType.CALIBRATION.labelResId())) },
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
                    onConfirmClick()
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
            if (preflightMessage != null) {
                PreflightWarningCard(
                    message = preflightMessage,
                    showMarkSensorChange = showMarkSensorChange,
                    canMarkSensorChange = canMarkSensorChange,
                    onMarkSensorChange = onMarkSensorChange
                )
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
                        decimalPlaces = uiState.bgDecimalPlaces
                    )
                }
            }

            Spacer(modifier = Modifier.height(AapsSpacing.medium))
        }
    }
}

@Composable
private fun PreflightWarningCard(
    message: String,
    showMarkSensorChange: Boolean,
    canMarkSensorChange: Boolean,
    onMarkSensorChange: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AapsSpacing.medium))
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
            if (showMarkSensorChange) {
                Spacer(modifier = Modifier.height(AapsSpacing.medium))
                OutlinedButton(
                    onClick = onMarkSensorChange,
                    enabled = canMarkSensorChange,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cal_precheck_mark_sensor_change))
                }
            }
        }
    }
}
