package app.aaps.ui.compose.extendedBolusDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import java.text.DecimalFormat
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

@Composable
fun ExtendedBolusDialogScreen(
    viewModel: ExtendedBolusDialogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is ExtendedBolusDialogViewModel.SideEffect.ShowDeliveryError -> {
                    onShowDeliveryError(effect.comment)
                }
            }
        }
    }

    // Dialog states
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }

    // Loop-stop warning dialog (shown before the main form)
    if (uiState.showLoopStopWarning && !uiState.loopStopWarningAccepted) {
        OkCancelDialog(
            title = stringResource(CoreUiR.string.extended_bolus),
            message = stringResource(CoreUiR.string.ebstopsloop),
            onConfirm = { viewModel.acceptLoopStopWarning() },
            onDismiss = { onNavigateBack() }
        )
    }

    // Confirmation dialog
    if (showConfirmation) {
        if (!viewModel.hasAction()) {
            showConfirmation = false
            showNoAction = true
        } else {
            val summaryLines = viewModel.buildConfirmationSummary()
            OkCancelDialog(
                title = stringResource(ElementType.EXTENDED_BOLUS.labelResId()),
                message = summaryLines.joinToString("<br/>"),
                icon = ElementType.EXTENDED_BOLUS.icon(),
                onConfirm = {
                    viewModel.confirmAndSave()
                    onNavigateBack()
                },
                onDismiss = { showConfirmation = false }
            )
        }
    }

    // No action dialog
    if (showNoAction) {
        OkCancelDialog(
            title = stringResource(ElementType.EXTENDED_BOLUS.labelResId()),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = ElementType.EXTENDED_BOLUS.icon(),
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    ExtendedBolusDialogContent(
        uiState = uiState,
        onInsulinChange = viewModel::updateInsulin,
        onDurationChange = viewModel::updateDuration,
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true }
    )
}

@Composable
private fun ExtendedBolusDialogContent(
    uiState: ExtendedBolusDialogUiState,
    onInsulinChange: (Double) -> Unit,
    onDurationChange: (Double) -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ElementType.EXTENDED_BOLUS.icon(),
                            contentDescription = null,
                            tint = ElementType.EXTENDED_BOLUS.color(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(ElementType.EXTENDED_BOLUS.labelResId()))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.back)
                        )
                    }
                },
                actions = {}
            )
        },
        bottomBar = {
            Button(
                onClick = onConfirmClick,
                enabled = uiState.insulin > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (uiState.insulin > 0.0) {
                    Text(stringResource(CoreUiR.string.format_insulin_units, uiState.insulin))
                } else {
                    Text(stringResource(CoreUiR.string.ok))
                }
            }
        }
    ) { paddingValues ->
        val itemModifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Single card: insulin + duration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NumberInputRow(
                        labelResId = CoreUiR.string.overview_insulin_label,
                        value = uiState.insulin,
                        onValueChange = onInsulinChange,
                        valueRange = uiState.extendedStep..uiState.maxInsulin,
                        step = uiState.extendedStep,
                        valueFormat = DecimalFormat("0.00"),
                        unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname),
                        modifier = itemModifier
                    )

                    NumberInputRow(
                        labelResId = CoreUiR.string.duration,
                        value = uiState.durationMinutes,
                        onValueChange = onDurationChange,
                        valueRange = uiState.extendedDurationStep..uiState.extendedMaxDuration,
                        step = uiState.extendedDurationStep,
                        valueFormat = DecimalFormat("0"),
                        unitLabelResId = KeysR.string.units_min,
                        modifier = itemModifier
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExtendedBolusDialogPreview() {
    MaterialTheme {
        ExtendedBolusDialogContent(
            uiState = ExtendedBolusDialogUiState(
                insulin = 1.0,
                durationMinutes = 30.0,
                maxInsulin = 10.0,
                extendedStep = 0.1,
                extendedDurationStep = 30.0,
                extendedMaxDuration = 720.0,
            ),
            onInsulinChange = {},
            onDurationChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}
