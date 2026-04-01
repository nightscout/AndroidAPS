package app.aaps.ui.compose.tempBasalDialog

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
fun TempBasalDialogScreen(
    viewModel: TempBasalDialogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is TempBasalDialogViewModel.SideEffect.ShowDeliveryError -> {
                    onShowDeliveryError(effect.comment)
                }
            }
        }
    }

    // Dialog states
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }

    // Confirmation dialog
    if (showConfirmation) {
        if (!viewModel.hasAction()) {
            showConfirmation = false
            showNoAction = true
        } else {
            val summaryLines = viewModel.buildConfirmationSummary()
            OkCancelDialog(
                title = stringResource(ElementType.TEMP_BASAL.labelResId()),
                message = summaryLines.joinToString("<br/>"),
                icon = ElementType.TEMP_BASAL.icon(),
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
            title = stringResource(ElementType.TEMP_BASAL.labelResId()),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = ElementType.TEMP_BASAL.icon(),
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    TempBasalDialogContent(
        uiState = uiState,
        onBasalPercentChange = viewModel::updateBasalPercent,
        onBasalAbsoluteChange = viewModel::updateBasalAbsolute,
        onDurationChange = viewModel::updateDuration,
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true }
    )
}

@Composable
private fun TempBasalDialogContent(
    uiState: TempBasalDialogUiState,
    onBasalPercentChange: (Double) -> Unit,
    onBasalAbsoluteChange: (Double) -> Unit,
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
                            imageVector = ElementType.TEMP_BASAL.icon(),
                            contentDescription = null,
                            tint = ElementType.TEMP_BASAL.color(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(ElementType.TEMP_BASAL.labelResId()))
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
            val hasAction = if (uiState.isPercentPump) uiState.basalPercent != 100.0 else uiState.basalAbsolute > 0.0
            Button(
                onClick = onConfirmClick,
                enabled = hasAction || uiState.durationMinutes > 0.0,
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
                if (uiState.isPercentPump && uiState.basalPercent != 100.0) {
                    Text("${DecimalFormat("0").format(uiState.basalPercent)}%")
                } else if (!uiState.isPercentPump && uiState.basalAbsolute > 0.0) {
                    Text("${DecimalFormat("0.00").format(uiState.basalAbsolute)} ${stringResource(CoreUiR.string.insulin_unit_shortname)}/h")
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
            // Single card: basal rate + duration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Percent or Absolute input
                    if (uiState.isPercentPump) {
                        NumberInputRow(
                            labelResId = CoreUiR.string.tempbasal_label,
                            value = uiState.basalPercent,
                            onValueChange = onBasalPercentChange,
                            valueRange = 0.0..uiState.maxTempPercent,
                            step = uiState.tempPercentStep,
                            valueFormat = DecimalFormat("0"),
                            unitLabel = "%",
                            modifier = itemModifier
                        )
                    } else {
                        NumberInputRow(
                            labelResId = CoreUiR.string.tempbasal_label,
                            value = uiState.basalAbsolute,
                            onValueChange = onBasalAbsoluteChange,
                            valueRange = 0.0..uiState.maxTempAbsolute,
                            step = uiState.tempAbsoluteStep,
                            valueFormat = DecimalFormat("0.00"),
                            unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname) + "/h",
                            modifier = itemModifier
                        )
                    }

                    // Duration
                    NumberInputRow(
                        labelResId = CoreUiR.string.duration,
                        value = uiState.durationMinutes,
                        onValueChange = onDurationChange,
                        valueRange = uiState.tempDurationStep..uiState.tempMaxDuration,
                        step = uiState.tempDurationStep,
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
private fun TempBasalDialogPercentPreview() {
    MaterialTheme {
        TempBasalDialogContent(
            uiState = TempBasalDialogUiState(
                basalPercent = 100.0,
                durationMinutes = 60.0,
                isPercentPump = true,
                maxTempPercent = 200.0,
                tempPercentStep = 10.0,
                tempDurationStep = 60.0,
                tempMaxDuration = 720.0,
            ),
            onBasalPercentChange = {},
            onBasalAbsoluteChange = {},
            onDurationChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TempBasalDialogAbsolutePreview() {
    MaterialTheme {
        TempBasalDialogContent(
            uiState = TempBasalDialogUiState(
                basalAbsolute = 0.85,
                durationMinutes = 60.0,
                isPercentPump = false,
                maxTempAbsolute = 10.0,
                tempAbsoluteStep = 0.05,
                tempDurationStep = 60.0,
                tempMaxDuration = 720.0,
            ),
            onBasalPercentChange = {},
            onBasalAbsoluteChange = {},
            onDurationChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}
