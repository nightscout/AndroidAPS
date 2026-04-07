package app.aaps.ui.compose.wizardDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import java.text.DecimalFormat
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.CarbTimeRow
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcBread
import app.aaps.core.ui.compose.icons.IcCake
import app.aaps.core.ui.compose.icons.IcPizza
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.core.ui.compose.preference.AdaptivePreferenceList
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.ui.R
import kotlinx.coroutines.launch
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

@Composable
fun WizardDialogScreen(
    viewModel: WizardDialogViewModel = hiltViewModel(),
    wizardSettingsDef: PreferenceSubScreenDef,
    onNavigateBack: () -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is WizardDialogViewModel.SideEffect.ShowDeliveryError -> {
                    onShowDeliveryError(effect.comment)
                }

                is WizardDialogViewModel.SideEffect.ShowTempBasalError -> {
                    onShowDeliveryError(effect.comment)
                }
            }
        }
    }

    // Dialog states (rememberSaveable to survive rotation)
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }
    var showBolusAdvisorPrompt by rememberSaveable { mutableStateOf(false) }
    var showAdvisorConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNormalConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Settings bottom sheet
    if (showSettings) {
        WizardSettingsSheet(
            settingsDef = wizardSettingsDef,
            viewModel = viewModel,
            onDismiss = {
                showSettings = false
                viewModel.refreshAfterSettings()
            }
        )
    }

    // --- Confirmation flow ---
    if (showConfirmation) {
        if (!viewModel.hasAction()) {
            showConfirmation = false
            showNoAction = true
        } else if (viewModel.needsBolusAdvisor()) {
            showConfirmation = false
            showBolusAdvisorPrompt = true
        } else {
            showConfirmation = false
            showNormalConfirmation = true
        }
    }

    // No action dialog
    if (showNoAction) {
        OkCancelDialog(
            title = stringResource(ElementType.BOLUS_WIZARD.labelResId()),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = ElementType.BOLUS_WIZARD.icon(),
            iconTint = ElementType.BOLUS_WIZARD.color(),
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    // Bolus advisor prompt: Yes / No / Cancel (3-button dialog)
    if (showBolusAdvisorPrompt) {
        AlertDialog(
            onDismissRequest = { showBolusAdvisorPrompt = false },
            icon = {
                Icon(
                    imageVector = ElementType.BOLUS_WIZARD.icon(),
                    contentDescription = null,
                    tint = ElementType.BOLUS_WIZARD.color()
                )
            },
            title = { Text(stringResource(CoreUiR.string.bolus_advisor)) },
            text = { Text(stringResource(CoreUiR.string.bolus_advisor_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBolusAdvisorPrompt = false
                    showAdvisorConfirmation = true
                }) {
                    Text(stringResource(CoreUiR.string.yes))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showBolusAdvisorPrompt = false
                        showNormalConfirmation = true
                    }) {
                        Text(stringResource(CoreUiR.string.no))
                    }
                    TextButton(onClick = { showBolusAdvisorPrompt = false }) {
                        Text(stringResource(CoreUiR.string.cancel))
                    }
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        )
    }

    // Advisor confirmation: show summary with advisor=true
    if (showAdvisorConfirmation) {
        val summaryLines = viewModel.getAdvisorSummary()
        OkCancelDialog(
            title = stringResource(ElementType.BOLUS_WIZARD.labelResId()),
            message = summaryLines.joinToString("<br/>"),
            icon = ElementType.BOLUS_WIZARD.icon(),
            iconTint = ElementType.BOLUS_WIZARD.color(),
            onConfirm = {
                viewModel.executeBolusAdvisor()
                onNavigateBack()
            },
            onDismiss = { showAdvisorConfirmation = false }
        )
    }

    // Normal confirmation: show summary with advisor=false
    if (showNormalConfirmation) {
        val summaryLines = viewModel.getConfirmationSummary()
        OkCancelDialog(
            title = stringResource(ElementType.BOLUS_WIZARD.labelResId()),
            message = summaryLines.joinToString("<br/>"),
            icon = ElementType.BOLUS_WIZARD.icon(),
            iconTint = ElementType.BOLUS_WIZARD.color(),
            onConfirm = {
                viewModel.executeNormal()
                onNavigateBack()
            },
            onDismiss = { showNormalConfirmation = false }
        )
    }

    WizardDialogContent(
        uiState = uiState,
        decimalFormatter = viewModel.decimalFormatter,
        profileUtil = viewModel.profileUtil,
        unitsLabel = uiState.units.asText,
        onBgChange = { viewModel.updateBg(it) },
        onCarbsChange = { viewModel.updateCarbs(it.toInt()) },
        onCarbsTypeChange = viewModel::updateCarbsType,
        onPercentageChange = { viewModel.updatePercentage(it.toInt()) },
        onDirectCorrectionChange = { viewModel.updateDirectCorrection(it) },
        onCarbTimeChange = { viewModel.updateCarbTime(it.toInt()) },
        onNotesChange = viewModel::updateNotes,
        onProfileSelect = viewModel::selectProfile,
        onBgToggle = viewModel::toggleBg,
        onTTToggle = viewModel::toggleTT,
        onTrendToggle = viewModel::toggleTrend,
        onIOBToggle = viewModel::toggleIOB,
        onCOBToggle = viewModel::toggleCOB,
        onAlarmToggle = viewModel::toggleAlarm,
        onAdvancedExpandToggle = viewModel::toggleAdvancedExpanded,
        onCalculationExpandToggle = viewModel::toggleCalculationExpanded,
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true },
        onSettingsClick = { showSettings = true }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardDialogContent(
    uiState: WizardDialogUiState,
    decimalFormatter: DecimalFormatter,
    profileUtil: ProfileUtil,
    unitsLabel: String,
    onBgChange: (Double) -> Unit,
    onCarbsChange: (Double) -> Unit,
    onCarbsTypeChange: (CarbsType) -> Unit,
    onPercentageChange: (Double) -> Unit,
    onDirectCorrectionChange: (Double) -> Unit,
    onCarbTimeChange: (Double) -> Unit,
    onNotesChange: (String) -> Unit,
    onProfileSelect: (Int) -> Unit,
    onBgToggle: (Boolean) -> Unit,
    onTTToggle: (Boolean) -> Unit,
    onTrendToggle: (Boolean) -> Unit,
    onIOBToggle: (Boolean) -> Unit,
    onCOBToggle: (Boolean) -> Unit,
    onAlarmToggle: (Boolean) -> Unit,
    onAdvancedExpandToggle: () -> Unit,
    onCalculationExpandToggle: () -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = ElementType.BOLUS_WIZARD.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = ElementType.BOLUS_WIZARD.color()
                        )
                        Text(stringResource(ElementType.BOLUS_WIZARD.labelResId()))
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
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(CoreUiR.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onConfirmClick,
                enabled = uiState.okVisible,
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
                    if (uiState.totalInsulin > 0.0) {
                        Text(stringResource(CoreUiR.string.format_insulin_units, uiState.totalInsulin))
                    }
                    if (uiState.totalInsulin > 0.0 && uiState.carbs > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (uiState.carbs > 0) {
                        Text(stringResource(CoreUiR.string.format_carbs, uiState.carbs))
                    }
                    if (!uiState.okVisible) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Calculation Card (expandable, at top) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header row with calculation result summary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCalculationExpandToggle() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(CoreUiR.string.wizard_calculation),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Result summary (always visible)
                            if (uiState.hasResult && (uiState.totalInsulin > 0.0 || uiState.carbs > 0)) {
                                if (uiState.totalInsulin > 0.0) {
                                    Text(
                                        text = stringResource(CoreUiR.string.format_insulin_units, uiState.totalInsulin),
                                        fontWeight = FontWeight.Bold,
                                        color = ElementType.INSULIN.color()
                                    )
                                }
                                if (uiState.carbs > 0) {
                                    Text(
                                        text = stringResource(CoreUiR.string.format_carbs, uiState.carbs),
                                        fontWeight = FontWeight.Bold,
                                        color = ElementType.CARBS.color()
                                    )
                                }
                                val hasExtra = uiState.percentage != 100 || uiState.directCorrection != 0.0
                                if (hasExtra) {
                                    Text("(", fontWeight = FontWeight.Bold)
                                    if (uiState.percentage != 100) {
                                        Text(
                                            text = stringResource(CoreUiR.string.format_percent, uiState.percentage),
                                            fontWeight = FontWeight.Bold,
                                            color = ElementType.CARBS.color()
                                        )
                                    }
                                    if (uiState.directCorrection != 0.0) {
                                        Text(
                                            text = stringResource(CoreUiR.string.format_insulin_units_signed, uiState.directCorrection),
                                            fontWeight = FontWeight.Bold,
                                            color = ElementType.INSULIN.color()
                                        )
                                    }
                                    Text(")", fontWeight = FontWeight.Bold)
                                }
                            } else if (uiState.hasResult && uiState.carbsEquivalent > 0) {
                                Text(
                                    text = stringResource(R.string.missing_carbs, uiState.carbsEquivalent.toInt()),
                                    fontWeight = FontWeight.Bold,
                                    color = ElementType.CARBS.color()
                                )
                            }
                            Icon(
                                imageVector = if (uiState.calculationExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Toggle buttons row (always visible)
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MultiChoiceSegmentedButtonRow(
                            modifier = Modifier.weight(1f)
                        ) {
                            // BG
                            SegmentedButton(
                                checked = uiState.useBg,
                                onCheckedChange = onBgToggle,
                                shape = SegmentedButtonDefaults.itemShape(0, 5),
                                icon = {}
                            ) {
                                Icon(
                                    imageVector = ElementType.BG_CHECK.icon(),
                                    contentDescription = stringResource(CoreUiR.string.wizard_include_bg),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // TT
                            SegmentedButton(
                                checked = uiState.useTT && uiState.hasTempTarget && uiState.useBg,
                                onCheckedChange = onTTToggle,
                                shape = SegmentedButtonDefaults.itemShape(1, 5),
                                enabled = uiState.hasTempTarget && uiState.useBg,
                                icon = {}
                            ) {
                                Icon(
                                    imageVector = IcTtManual,
                                    contentDescription = stringResource(CoreUiR.string.wizard_include_tt),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // Trend
                            SegmentedButton(
                                checked = uiState.useTrend,
                                onCheckedChange = onTrendToggle,
                                shape = SegmentedButtonDefaults.itemShape(2, 5),
                                icon = {}
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = stringResource(CoreUiR.string.wizard_include_trend),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // IOB
                            SegmentedButton(
                                checked = uiState.useIOB,
                                onCheckedChange = onIOBToggle,
                                shape = SegmentedButtonDefaults.itemShape(3, 5),
                                icon = {}
                            ) {
                                Icon(
                                    imageVector = ElementType.INSULIN.icon(),
                                    contentDescription = stringResource(CoreUiR.string.wizard_include_iob),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // COB
                            SegmentedButton(
                                checked = uiState.useCOB,
                                onCheckedChange = onCOBToggle,
                                shape = SegmentedButtonDefaults.itemShape(4, 5),
                                icon = {}
                            ) {
                                Icon(
                                    imageVector = ElementType.COB.icon(),
                                    contentDescription = stringResource(CoreUiR.string.wizard_include_cob),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        val includeTooltipState = rememberTooltipState()
                        val includeScope = rememberCoroutineScope()
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(R.string.wizard_include_info))
                                }
                            },
                            state = includeTooltipState
                        ) {
                            IconButton(
                                onClick = { includeScope.launch { includeTooltipState.show() } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.calculationExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (uiState.hasResult) {
                                // === Suggestion components (scaled by percentage) ===

                                // BG
                                if (uiState.useBg && uiState.bg > 0) {
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.wizard_bg_label) + " (ISF: ${decimalFormatter.to1Decimal(uiState.isf)})",
                                        value = stringResource(CoreUiR.string.format_insulin_units, uiState.insulinFromBG)
                                    )
                                }

                                // Trend
                                if (uiState.useTrend) {
                                    CalcRow(
                                        label = uiState.trendDetail,
                                        value = stringResource(CoreUiR.string.format_insulin_units, uiState.insulinFromTrend)
                                    )
                                }

                                // COB
                                if (uiState.useCOB) {
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.cob) + " (IC: ${decimalFormatter.to1Decimal(uiState.ic)})",
                                        value = stringResource(CoreUiR.string.format_insulin_units, uiState.insulinFromCOB)
                                    )
                                }

                                // Carbs
                                if (uiState.eCarbs > 0) {
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.carbs) + " ${uiState.effectiveCarbs}g (IC: ${decimalFormatter.to1Decimal(uiState.ic)})",
                                        value = stringResource(CoreUiR.string.format_insulin_units, uiState.insulinFromCarbs)
                                    )
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.wizard_ecarbs, uiState.eCarbs, uiState.eCarbsDurationHours, uiState.eCarbsDelayMinutes),
                                        value = ""
                                    )
                                } else {
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.carbs) + " (IC: ${decimalFormatter.to1Decimal(uiState.ic)})",
                                        value = stringResource(CoreUiR.string.format_insulin_units, uiState.insulinFromCarbs)
                                    )
                                }

                                if (uiState.percentage != 100) {
                                    // Show subtotal before percentage, then scaled result
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    val scaledSubtotal = uiState.insulinFromBG + uiState.insulinFromTrend +
                                        uiState.insulinFromCarbs + uiState.insulinFromCOB
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.wizard_subtotal),
                                        value = stringResource(CoreUiR.string.format_insulin_units, scaledSubtotal)
                                    )
                                    val afterPercentage = scaledSubtotal * uiState.percentage / 100.0
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.format_percent, uiState.percentage),
                                        value = stringResource(CoreUiR.string.format_insulin_units, afterPercentage)
                                    )
                                }

                                // === Fact components (not scaled by percentage) ===

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // IOB
                                if (uiState.useIOB) {
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.iob),
                                        value = stringResource(CoreUiR.string.format_insulin_units, uiState.totalIOB)
                                    )
                                }

                                // Direct Correction
                                if (uiState.insulinFromCorrection != 0.0) {
                                    CalcRow(
                                        label = stringResource(CoreUiR.string.wizard_correction),
                                        value = stringResource(CoreUiR.string.format_insulin_units, uiState.insulinFromCorrection)
                                    )
                                }

                                // === Total ===
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                CalcRow(
                                    label = stringResource(CoreUiR.string.wizard_total),
                                    value = stringResource(CoreUiR.string.format_insulin_units, uiState.totalInsulin)
                                )
                            }
                        }
                    }
                }
            }

            // --- All inputs in one card (gap-as-divider pattern) ---
            val itemModifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp)

            val bgIsOld = !uiState.hasBgData || uiState.bgAgeMinutes > 9
            var bgExpanded by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(bgIsOld) {
                if (bgIsOld) bgExpanded = true
            }
            var percentageExpanded by rememberSaveable { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Carbs Input
                Column(modifier = itemModifier) {
                    NumberInputRow(
                        labelResId = CoreUiR.string.carbs,
                        value = uiState.carbs.toDouble(),
                        onValueChange = onCarbsChange,
                        valueRange = 0.0..uiState.maxCarbs.toDouble(),
                        step = 1.0,
                        unitLabel = "g"
                    )

                    // Carbs type selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.weight(1f)
                        ) {
                            CarbsType.entries.forEachIndexed { index, type ->
                                SegmentedButton(
                                    selected = uiState.carbsType == type,
                                    onClick = { onCarbsTypeChange(type) },
                                    shape = SegmentedButtonDefaults.itemShape(index, CarbsType.entries.size),
                                    icon = {}
                                ) {
                                    Icon(
                                        imageVector = when (type) {
                                            CarbsType.BREAD -> IcBread
                                            CarbsType.CAKE  -> IcCake
                                            CarbsType.PIZZA -> IcPizza
                                        },
                                        contentDescription = type.name,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        val tooltipState = rememberTooltipState()
                        val scope = rememberCoroutineScope()
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    Column {
                                        Text(
                                            text = when (uiState.carbsType) {
                                                CarbsType.BREAD -> stringResource(CoreUiR.string.carbs_type_bread)
                                                CarbsType.CAKE  -> stringResource(CoreUiR.string.carbs_type_cake)
                                                CarbsType.PIZZA -> stringResource(CoreUiR.string.carbs_type_pizza)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val type = uiState.carbsType
                                        Text(
                                            text = if (type == CarbsType.BREAD)
                                                stringResource(R.string.wizard_carbs_type_bread_desc)
                                            else
                                                stringResource(
                                                    R.string.wizard_carbs_type_desc,
                                                    100 - type.carbsPercent,
                                                    type.eCarbsPercent,
                                                    type.eCarbsDelayMinutes,
                                                    type.eCarbsDurationHours
                                                ),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            state = tooltipState
                        ) {
                            IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Direct Correction input
                NumberInputRow(
                    labelResId = CoreUiR.string.wizard_correction,
                    value = uiState.directCorrection,
                    onValueChange = onDirectCorrectionChange,
                    valueRange = -uiState.maxBolus..uiState.maxBolus,
                    step = uiState.bolusStep,
                    unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname),
                    decimalPlaces = 2,
                    modifier = itemModifier
                )

                // Carb Time (compact row with popup dialog)
                CarbTimeRow(
                    offsetMinutes = uiState.carbTime,
                    alarmChecked = uiState.alarmChecked,
                    onOffsetChange = { onCarbTimeChange(it.toDouble()) },
                    onAlarmChange = onAlarmToggle,
                    modifier = itemModifier
                )
                // BG (collapsible, auto-expand when old/missing)
                Column(modifier = itemModifier) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(CoreUiR.string.wizard_bg_label) + ": ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.hasBgData) {
                                val bgFormat = if (uiState.isMgdl) DecimalFormat("0") else DecimalFormat("0.0")
                                Text(
                                    text = "${bgFormat.format(uiState.bg)} $unitsLabel",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "(${uiState.bgAgeMinutes} min)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (bgIsOld) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = stringResource(CoreUiR.string.not_available_full),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (!bgExpanded) {
                            FilledTonalButton(onClick = { bgExpanded = true }) {
                                Text(stringResource(CoreUiR.string.change))
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = bgExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        NumberInputRow(
                            labelResId = CoreUiR.string.wizard_bg_label,
                            value = uiState.bg,
                            onValueChange = onBgChange,
                            valueRange = uiState.bgRange,
                            step = uiState.bgStep,
                            unitLabel = unitsLabel,
                            decimalPlaces = if (uiState.isMgdl) 0 else 1
                        )
                    }
                }

                // Percentage (collapsible)
                Column(modifier = itemModifier) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(CoreUiR.string.wizard_use_percentage) + ": ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.percentage}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!percentageExpanded) {
                            FilledTonalButton(onClick = { percentageExpanded = true }) {
                                Text(stringResource(CoreUiR.string.change))
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = percentageExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        NumberInputRow(
                            labelResId = CoreUiR.string.wizard_use_percentage,
                            value = uiState.percentage.toDouble(),
                            onValueChange = onPercentageChange,
                            valueRange = 10.0..200.0,
                            step = 5.0,
                            unitLabel = "%",
                            decimalPlaces = 0
                        )
                    }
                }

                // Profile (collapsible, hidden in simple mode)
                if (!uiState.simpleMode) {
                    val currentProfile = uiState.profileNames.getOrElse(uiState.selectedProfileIndex) { "" }
                    var profileExpanded by rememberSaveable { mutableStateOf(false) }

                    Column(modifier = itemModifier) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(CoreUiR.string.profile) + ": ",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentProfile,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!profileExpanded) {
                                FilledTonalButton(onClick = { profileExpanded = true }) {
                                    Text(stringResource(CoreUiR.string.change))
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = profileExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            ProfileDropdown(
                                profileNames = uiState.profileNames,
                                selectedIndex = uiState.selectedProfileIndex,
                                onSelect = onProfileSelect
                            )
                        }
                    }
                }
                // Notes
                if (uiState.showNotes) {
                    TextField(
                        value = uiState.notes,
                        onValueChange = onNotesChange,
                        label = { Text(stringResource(CoreUiR.string.notes_label)) },
                        modifier = itemModifier,
                        singleLine = false,
                        maxLines = 3
                    )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CalcRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardSettingsSheet(
    settingsDef: PreferenceSubScreenDef,
    viewModel: WizardDialogViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(
                text = stringResource(settingsDef.titleResId),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            )
            ProvidePreferenceTheme {
                AdaptivePreferenceList(
                    items = settingsDef.items
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(
    profileNames: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = profileNames.getOrElse(selectedIndex) { "" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(CoreUiR.string.profile)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profileNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
