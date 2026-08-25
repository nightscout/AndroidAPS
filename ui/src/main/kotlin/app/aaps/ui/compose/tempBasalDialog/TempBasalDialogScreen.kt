package app.aaps.ui.compose.tempBasalDialog

import app.aaps.core.ui.compose.stringResourceOrNull
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.metroViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.bottomBarSafeArea
import app.aaps.core.ui.compose.dialogs.ElementConfirmationDialog
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.interfaces.R as InterfacesR

@Composable
fun TempBasalDialogScreen(
    viewModel: TempBasalDialogViewModel = metroViewModel(),
    onNavigateBack: () -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The master's prepared confirmation (bolusId + its lines), set via the ShowConfirmation side effect.
    var confirmation by remember { mutableStateOf<Pair<Long, List<ConfirmationLine>>?>(null) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is TempBasalDialogViewModel.SideEffect.ShowDeliveryError -> onShowDeliveryError(effect.comment)
                is TempBasalDialogViewModel.SideEffect.ShowNoActionDialog -> showNoAction = true
                is TempBasalDialogViewModel.SideEffect.ShowConfirmation -> confirmation = effect.bolusId to effect.lines
            }
        }
    }

    // Confirmation dialog — renders the MASTER's prepared lines (set via ShowConfirmation after prepare()).
    confirmation?.let { (bolusId, lines) ->
        ElementConfirmationDialog(
            elementType = ElementType.TEMP_BASAL,
            lines = lines,
            onConfirm = {
                viewModel.commit(bolusId)
                confirmation = null
                onNavigateBack()
            },
            onDismiss = { confirmation = null }
        )
    }

    // No action dialog
    if (showNoAction) {
        ElementConfirmationDialog(
            elementType = ElementType.TEMP_BASAL,
            message = stringResource(CoreUiR.string.no_action_selected),
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
        onConfirmClick = { viewModel.prepareAndConfirm() }
    )
}

/**
 * @see TempBasalDialogPercentPreview
 * @see TempBasalDialogAbsolutePreview
 */
@Composable
internal fun TempBasalDialogContent(
    uiState: TempBasalDialogUiState,
    onBasalPercentChange: (Double) -> Unit,
    onBasalAbsoluteChange: (Double) -> Unit,
    onDurationChange: (Double) -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text((stringResourceOrNull(ElementType.TEMP_BASAL.label()) ?: "")) },
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
        bottomBar = {
            val hasAction = if (uiState.isPercentPump) uiState.basalPercent != 100.0 else uiState.basalAbsolute > 0.0
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onConfirmClick()
                },
                enabled = (hasAction || uiState.durationMinutes > 0.0) && !uiState.isPreparing,
                modifier = Modifier
                    .fillMaxWidth()
                    .bottomBarSafeArea()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (uiState.isPercentPump && uiState.basalPercent != 100.0) {
                    Text("${NumberFormat.INTEGER.format(uiState.basalPercent)}%")
                } else if (!uiState.isPercentPump && uiState.basalAbsolute > 0.0) {
                    Text("${NumberFormat.DECIMAL_2.format(uiState.basalAbsolute)} ${stringResource(InterfacesR.string.profile_ins_units_per_hour)}")
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
                            valueFormat = NumberFormat.INTEGER,
                            unitLabel = TextRef.Literal("%"),
                            modifier = itemModifier
                        )
                    } else {
                        NumberInputRow(
                            labelResId = CoreUiR.string.tempbasal_label,
                            value = uiState.basalAbsolute,
                            onValueChange = onBasalAbsoluteChange,
                            valueRange = 0.0..uiState.maxTempAbsolute,
                            step = uiState.tempAbsoluteStep,
                            valueFormat = NumberFormat.DECIMAL_2,
                            unitLabel = TextRef.AndroidRes(InterfacesR.string.profile_ins_units_per_hour),
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
                        valueFormat = NumberFormat.INTEGER,
                        unitLabel = TextRef.AndroidRes(CoreUiR.string.units_min),
                        modifier = itemModifier
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
