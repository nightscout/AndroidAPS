package app.aaps.ui.compose.treatmentDialog

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
import app.aaps.ui.compose.components.DialogStatusBar
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
import app.aaps.ui.compose.overview.graphs.CobUiState
import app.aaps.ui.compose.overview.graphs.IobUiState
import kotlinx.coroutines.flow.StateFlow
import java.text.DecimalFormat
import app.aaps.core.ui.R as CoreUiR

@Composable
fun TreatmentDialogScreen(
    viewModel: TreatmentDialogViewModel = hiltViewModel(),
    bgInfoState: StateFlow<BgInfoUiState>,
    iobUiState: StateFlow<IobUiState>,
    cobUiState: StateFlow<CobUiState>,
    onNavigateBack: () -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bgInfo by bgInfoState.collectAsStateWithLifecycle()
    val iob by iobUiState.collectAsStateWithLifecycle()
    val cob by cobUiState.collectAsStateWithLifecycle()

    // Dialog states
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is TreatmentDialogViewModel.SideEffect.ShowDeliveryError -> {
                    onShowDeliveryError(effect.comment)
                }

                is TreatmentDialogViewModel.SideEffect.ShowNoActionDialog -> {
                    showNoAction = true
                }
            }
        }
    }

    // Confirmation dialog
    if (showConfirmation) {
        if (!viewModel.hasAction()) {
            showConfirmation = false
            showNoAction = true
        } else {
            val summaryLines = viewModel.buildConfirmationSummary()
            OkCancelDialog(
                title = stringResource(ElementType.TREATMENT.labelResId()),
                message = summaryLines.joinToString("<br/>"),
                icon = ElementType.TREATMENT.icon(),
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
            title = stringResource(ElementType.TREATMENT.labelResId()),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = ElementType.TREATMENT.icon(),
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    TreatmentDialogContent(
        uiState = uiState,
        bgInfo = bgInfo,
        iob = iob,
        cob = cob,
        bolusFormat = viewModel.decimalFormatter.pumpSupportedBolusFormat(uiState.bolusStep),
        onInsulinChange = { viewModel.updateInsulin(it) },
        onCarbsChange = { viewModel.updateCarbs(it.toInt()) },
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true }
    )
}

@Composable
private fun TreatmentDialogContent(
    uiState: TreatmentDialogUiState,
    bgInfo: BgInfoUiState,
    iob: IobUiState,
    cob: CobUiState,
    bolusFormat: DecimalFormat,
    onInsulinChange: (Double) -> Unit,
    onCarbsChange: (Double) -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ElementType.TREATMENT.icon(),
                            contentDescription = null,
                            tint = ElementType.TREATMENT.color(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(ElementType.TREATMENT.labelResId()))
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
            val hasAction = uiState.insulin > 0.0 || uiState.carbs > 0
            Button(
                onClick = onConfirmClick,
                enabled = hasAction,
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
                }
                if (uiState.insulin > 0.0 && uiState.carbs > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (uiState.carbs > 0) {
                    Text(stringResource(CoreUiR.string.format_carbs, uiState.carbs))
                }
                if (!hasAction) {
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
            // --- Status Bar ---
            DialogStatusBar(bgInfo = bgInfo, iob = iob, cob = cob)

            // --- Single Card: Insulin + Carbs ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NumberInputRow(
                        labelResId = CoreUiR.string.overview_insulin_label,
                        value = uiState.insulin,
                        onValueChange = onInsulinChange,
                        valueRange = 0.0..uiState.maxInsulin,
                        step = uiState.bolusStep,
                        valueFormat = bolusFormat,
                        unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname),
                        modifier = itemModifier
                    )

                    NumberInputRow(
                        labelResId = CoreUiR.string.carbs,
                        value = uiState.carbs.toDouble(),
                        onValueChange = onCarbsChange,
                        valueRange = 0.0..uiState.maxCarbs.toDouble(),
                        step = 1.0,
                        valueFormat = DecimalFormat("0"),
                        unitLabel = stringResource(CoreUiR.string.shortgramm),
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
private fun TreatmentDialogScreenPreview() {
    MaterialTheme {
        TreatmentDialogContent(
            uiState = TreatmentDialogUiState(
                insulin = 1.5,
                carbs = 20,
                maxInsulin = 10.0,
                maxCarbs = 100,
                bolusStep = 0.1
            ),
            bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
            iob = IobUiState(),
            cob = CobUiState(),
            bolusFormat = DecimalFormat("0.0"),
            onInsulinChange = {},
            onCarbsChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}