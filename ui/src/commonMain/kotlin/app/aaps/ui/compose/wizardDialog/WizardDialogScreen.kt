package app.aaps.ui.compose.wizardDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.CarbsType
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ExpandableNumberInputRow
import app.aaps.core.ui.compose.ExpandableRowHeader
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.QuickAddButtons
import app.aaps.core.ui.compose.TimeOffsetRow
import app.aaps.core.ui.compose.bottomBarSafeArea
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.dialogs.ElementConfirmationDialog
import app.aaps.core.ui.compose.icons.IcBread
import app.aaps.core.ui.compose.icons.IcCake
import app.aaps.core.ui.compose.icons.IcPizza
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.preference.PreferenceSheetContent
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.rememberBringIntoViewOnExpand
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.components.RecordOnlyBanner
import kotlinx.coroutines.launch

@Composable
fun WizardDialogScreen(
    viewModel: WizardDialogViewModel = metroViewModel(),
    wizardSettingsDef: PreferenceSubScreenDef,
    onNavigateBack: () -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is WizardDialogViewModel.SideEffect.ShowDeliveryError  -> {
                    onShowDeliveryError(effect.comment)
                }

                is WizardDialogViewModel.SideEffect.ShowTempBasalError -> {
                    onShowDeliveryError(effect.comment)
                }

                is WizardDialogViewModel.SideEffect.NavigateBack       -> {
                    onNavigateBack()
                }
            }
        }
    }

    // Dialog states (rememberSaveable to survive rotation)
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }
    var showRecordOnly by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Settings bottom sheet
    if (showSettings) {
        WizardSettingsSheet(
            settingsDef = wizardSettingsDef,
            onDismiss = {
                showSettings = false
                viewModel.refreshAfterSettings()
            }
        )
    }

    // --- Confirmation flow ---
    if (showConfirmation) {
        showConfirmation = false
        when {
            !viewModel.hasAction()   -> showNoAction = true
            // Master can't deliver → log the wizard calc locally (record-only). Master-only; a client always delivers.
            uiState.forcedRecordOnly -> showRecordOnly = true
            // Deliver role-transparently: the master recomputes, caps + authors the confirmation and shows it on the
            // single app-level dialog (both roles); the advisor fork is decided from the master's prepared result.
            // The wizard stays open behind the confirmation — it closes only after the user confirms (via the
            // NavigateBack side effect from deliverManualWizard); Cancel keeps it open with inputs intact.
            else                     -> viewModel.deliverManualWizard()
        }
    }

    // No action dialog
    if (showNoAction) {
        ElementConfirmationDialog(
            elementType = ElementType.BOLUS_WIZARD,
            message = stringResource(CoreUiStrings.no_action_selected),
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    // Record-only confirmation (master can't deliver): locally-built lines, persisted on confirm. The delivery path
    // shows the master-authored confirmation on the single app-level dialog instead (see deliverManualWizard).
    if (showRecordOnly) {
        ElementConfirmationDialog(
            elementType = ElementType.BOLUS_WIZARD,
            lines = viewModel.getConfirmationSummary(),
            onConfirm = {
                viewModel.recordOnly()
                onNavigateBack()
            },
            onDismiss = { showRecordOnly = false }
        )
    }

    WizardDialogContent(
        uiState = uiState,
        decimalFormatter = viewModel.decimalFormatter,
        unitsLabel = uiState.units.displayLabel,
        onBgChange = { viewModel.updateBg(it) },
        onCarbsChange = { viewModel.updateCarbs(it.toInt()) },
        onAddCarbs = viewModel::addCarbs,
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
        onCalculationExpandToggle = viewModel::toggleCalculationExpanded,
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true },
        onSettingsClick = { showSettings = true }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WizardDialogContent(
    uiState: WizardDialogUiState,
    decimalFormatter: DecimalFormatter,
    unitsLabel: String,
    onBgChange: (Double) -> Unit,
    onCarbsChange: (Double) -> Unit,
    onAddCarbs: (Int) -> Unit,
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
    onCalculationExpandToggle: () -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            Column {
                AapsTopAppBar(
                    title = { Text((stringResourceOrNull(ElementType.BOLUS_WIZARD.label()) ?: "")) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(CoreUiStrings.close)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(CoreUiStrings.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                if (uiState.forcedRecordOnly) RecordOnlyBanner()
            }
        },
        bottomBar = {
            // The button shows only the amounts, so a screen reader would say just "2.5 U" and never
            // what pressing it does. The verb only: the Texts below are still read, so repeating the
            // amounts here would have them announced twice. This also sidesteps the insulin/carbs
            // branching, which no single template could join without leaving a gap.
            val confirmDescription = stringResource(CoreUiStrings.confirm)
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onConfirmClick()
                },
                enabled = uiState.okVisible,
                modifier = Modifier
                    .fillMaxWidth()
                    .bottomBarSafeArea()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = confirmDescription }
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (uiState.totalInsulin > 0.0) {
                    Text(stringResource(InterfacesStrings.format_insulin_units, uiState.totalInsulin))
                }
                if (uiState.totalInsulin > 0.0 && (uiState.effectiveCarbs > 0 || uiState.eCarbs > 0)) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (uiState.effectiveCarbs > 0 || uiState.eCarbs > 0) {
                    Text(
                        if (uiState.eCarbs > 0) stringResource(CoreUiStrings.format_carbs_split, uiState.effectiveCarbs, uiState.eCarbs)
                        else stringResource(InterfacesStrings.format_carbs, uiState.effectiveCarbs)
                    )
                }
                if (!uiState.okVisible) {
                    Text(stringResource(CoreUiStrings.ok))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeOverscroll()
                .verticalScroll(rememberScrollState())
                .clearFocusOnTap(focusManager)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Calculation Card (expandable, at top) ---
            val calculationExpandRequester = rememberBringIntoViewOnExpand(uiState.calculationExpanded)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(calculationExpandRequester),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header row with calculation result summary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCalculationExpandToggle() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // The title gives way first: it is the weighted child, so it is measured
                        // after the values and gets only the space they leave, shrinking its font
                        // down to the minimum before it is cut. On a narrow screen the summary
                        // used to be cut off instead, and the summary is what matters here.
                        BasicText(
                            text = stringResource(CoreUiStrings.wizard_calculation),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = LocalContentColor.current
                            ),
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp, stepSize = 1.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // Last resort when the values alone are wider than the row (very big
                        // font): a value that does not fit moves to the next line as a whole,
                        // still right-aligned.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            // Result summary (always visible)
                            if (uiState.hasResult && (uiState.totalInsulin > 0.0 || uiState.carbs > 0)) {
                                if (uiState.totalInsulin > 0.0) {
                                    Text(
                                        text = stringResource(InterfacesStrings.format_insulin_units, uiState.totalInsulin),
                                        fontWeight = FontWeight.Bold,
                                        color = ElementType.INSULIN.color()
                                    )
                                }
                                if (uiState.effectiveCarbs > 0 || uiState.eCarbs > 0) {
                                    Text(
                                        text = if (uiState.eCarbs > 0) stringResource(CoreUiStrings.format_carbs_split, uiState.effectiveCarbs, uiState.eCarbs)
                                        else stringResource(InterfacesStrings.format_carbs, uiState.effectiveCarbs),
                                        fontWeight = FontWeight.Bold,
                                        color = ElementType.CARBS.color()
                                    )
                                }
                                val hasPercentage = uiState.percentage != 100
                                val hasCorrection = uiState.directCorrection != 0.0
                                if (hasPercentage || hasCorrection) {
                                    val percentageText = stringResource(CoreUiStrings.format_percent, uiState.percentage)
                                    val correctionText = stringResource(CoreUiStrings.format_insulin_units_signed, uiState.directCorrection)
                                    val extras = when {
                                        hasPercentage && hasCorrection -> stringResource(UiStrings.wizard_percentage_and_correction, percentageText, correctionText)
                                        hasPercentage                  -> percentageText
                                        else                           -> correctionText
                                    }
                                    val extrasText = stringResource(CoreUiStrings.value_in_parentheses, extras)
                                    val percentageColor = ElementType.CARBS.color()
                                    val correctionColor = ElementType.INSULIN.color()
                                    // One text from the templates, so a translator controls the brackets and
                                    // the order. Only the two values get their own colour.
                                    Text(
                                        text = buildAnnotatedString {
                                            append(extrasText)
                                            if (hasPercentage) colorPart(extrasText, percentageText, percentageColor)
                                            if (hasCorrection) colorPart(extrasText, correctionText, correctionColor)
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (uiState.hasResult && uiState.carbsEquivalent > 0) {
                                Text(
                                    text = stringResource(UiStrings.missing_carbs, uiState.carbsEquivalent.toInt()),
                                    fontWeight = FontWeight.Bold,
                                    color = ElementType.CARBS.color()
                                )
                            }
                        }
                        // Outside the FlowRow, so it never wraps to the second line.
                        Icon(
                            imageVector = if (uiState.calculationExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = stringResource(if (uiState.calculationExpanded) CoreUiStrings.collapse else CoreUiStrings.expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                    contentDescription = stringResource(CoreUiStrings.wizard_include_bg),
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
                                    contentDescription = stringResource(CoreUiStrings.wizard_include_tt),
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
                                    contentDescription = stringResource(CoreUiStrings.wizard_include_trend),
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
                                    contentDescription = stringResource(CoreUiStrings.wizard_include_iob),
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
                                    contentDescription = stringResource(CoreUiStrings.wizard_include_cob),
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
                                    Text(stringResource(UiStrings.wizard_include_info))
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
                                    contentDescription = stringResource(CoreUiStrings.carousel_show_card),
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
                                        label = stringResource(UiStrings.wizard_calc_with_isf, stringResource(CoreUiStrings.wizard_bg_label), decimalFormatter.to1Decimal(uiState.isf)),
                                        value = stringResource(InterfacesStrings.format_insulin_units, uiState.insulinFromBG)
                                    )
                                }

                                // Trend
                                if (uiState.useTrend) {
                                    CalcRow(
                                        label = uiState.trendDetail,
                                        value = stringResource(InterfacesStrings.format_insulin_units, uiState.insulinFromTrend)
                                    )
                                }

                                // COB
                                if (uiState.useCOB) {
                                    CalcRow(
                                        label = stringResource(UiStrings.wizard_calc_with_ic, stringResource(CoreUiStrings.cob), decimalFormatter.to1Decimal(uiState.ic)),
                                        value = stringResource(InterfacesStrings.format_insulin_units, uiState.insulinFromCOB)
                                    )
                                }

                                // Carbs
                                if (uiState.eCarbs > 0) {
                                    CalcRow(
                                        label = stringResource(
                                            UiStrings.wizard_calc_carbs_with_ic,
                                            stringResource(InterfacesStrings.carbs),
                                            stringResource(InterfacesStrings.format_carbs, uiState.effectiveCarbs),
                                            decimalFormatter.to1Decimal(uiState.ic)
                                        ),
                                        value = stringResource(InterfacesStrings.format_insulin_units, uiState.insulinFromCarbs)
                                    )
                                    CalcRow(
                                        label = stringResource(InterfacesStrings.wizard_ecarbs, uiState.eCarbs, uiState.eCarbsDurationHours, uiState.eCarbsDelayMinutes),
                                        value = ""
                                    )
                                } else {
                                    CalcRow(
                                        label = stringResource(UiStrings.wizard_calc_with_ic, stringResource(InterfacesStrings.carbs), decimalFormatter.to1Decimal(uiState.ic)),
                                        value = stringResource(InterfacesStrings.format_insulin_units, uiState.insulinFromCarbs)
                                    )
                                }

                                if (uiState.percentage != 100) {
                                    // Show subtotal before percentage, then scaled result
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    val scaledSubtotal = uiState.insulinFromBG + uiState.insulinFromTrend +
                                        uiState.insulinFromCarbs + uiState.insulinFromCOB
                                    CalcRow(
                                        label = stringResource(CoreUiStrings.wizard_subtotal),
                                        value = stringResource(InterfacesStrings.format_insulin_units, scaledSubtotal)
                                    )
                                    val afterPercentage = scaledSubtotal * uiState.percentage / 100.0
                                    CalcRow(
                                        label = stringResource(CoreUiStrings.format_percent, uiState.percentage),
                                        value = stringResource(InterfacesStrings.format_insulin_units, afterPercentage)
                                    )
                                }

                                // === Fact components (not scaled by percentage) ===

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // IOB
                                if (uiState.useIOB) {
                                    CalcRow(
                                        label = stringResource(CoreUiStrings.iob),
                                        value = stringResource(InterfacesStrings.format_insulin_units, uiState.totalIOB)
                                    )
                                }

                                // Direct Correction
                                if (uiState.insulinFromCorrection != 0.0) {
                                    CalcRow(
                                        label = stringResource(CoreUiStrings.wizard_correction),
                                        value = stringResource(InterfacesStrings.format_insulin_units, uiState.insulinFromCorrection)
                                    )
                                }

                                // === Total ===
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                CalcRow(
                                    label = stringResource(CoreUiStrings.wizard_total),
                                    value = stringResource(InterfacesStrings.format_insulin_units, uiState.totalInsulin)
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
            // Wait for the real values. The first frames show the default state, which has no BG, so
            // without this check the BG input opened every time and stayed open for a fresh BG too.
            LaunchedEffect(uiState.initialized, bgIsOld) {
                if (uiState.initialized && bgIsOld) bgExpanded = true
            }
            // Scroll to the BG input only when the user opened it. When it opens by itself (old BG),
            // a scroll would move the top of the screen away before the user has read it.
            var bgExpandedByUser by remember { mutableStateOf(false) }
            var percentageExpanded by rememberSaveable { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Carbs Input
                    Column(modifier = itemModifier) {
                        NumberInputRow(
                            labelRef = InterfacesStrings.carbs,
                            value = uiState.carbs.toDouble(),
                            onValueChange = onCarbsChange,
                            valueRange = 0.0..uiState.maxCarbs.toDouble(),
                            step = 1.0,
                            unitLabel = TextRef.Literal("g")
                        )
                        QuickAddButtons(
                            increment1 = uiState.carbsButtonIncrement1,
                            increment2 = uiState.carbsButtonIncrement2,
                            increment3 = uiState.carbsButtonIncrement3,
                            onAddCarbs = onAddCarbs
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
                                            // Was the enum name ("BREAD"), untranslated and saying
                                            // nothing about what the choice does to the carbs.
                                            contentDescription = carbsTypeEffect(type),
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
                                                    CarbsType.BREAD -> stringResource(CoreUiStrings.carbs_type_bread)
                                                    CarbsType.CAKE  -> stringResource(CoreUiStrings.carbs_type_cake)
                                                    CarbsType.PIZZA -> stringResource(CoreUiStrings.carbs_type_pizza)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = carbsTypeEffect(uiState.carbsType),
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
                                        contentDescription = stringResource(CoreUiStrings.carousel_show_card),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Direct Correction: collapsed, so there is no second open field under the carbs
                    // one. They looked alike, and grams typed into this one were bolused as units
                    // (reported on Discord). Nudging still works from the collapsed row; the pencil
                    // opens the field for a value that would take too many steps.
                    ExpandableNumberInputRow(
                        labelRef = CoreUiStrings.wizard_correction,
                        value = uiState.directCorrection,
                        onValueChange = onDirectCorrectionChange,
                        valueRange = -uiState.maxBolus..uiState.maxBolus,
                        step = uiState.bolusStep,
                        unitLabel = CoreUiStrings.insulin_unit_shortname,
                        decimalPlaces = 2,
                        modifier = itemModifier
                    )

                    // Carb Time (compact row with popup dialog)
                    TimeOffsetRow(
                        labelRef = CoreUiStrings.wizard_carb_time,
                        offsetMinutes = uiState.carbTime,
                        onOffsetChange = { onCarbTimeChange(it.toDouble()) },
                        alarmChecked = uiState.alarmChecked,
                        onAlarmChange = onAlarmToggle,
                        modifier = itemModifier
                    )
                    // BG (collapsible, auto-expand when old/missing)
                    val bgExpandRequester = rememberBringIntoViewOnExpand(bgExpandedByUser)
                    ExpandableNumberInputRow(
                        labelRef = CoreUiStrings.wizard_bg_label,
                        value = uiState.bg,
                        onValueChange = onBgChange,
                        valueRange = uiState.bgRange,
                        step = uiState.bgStep,
                        unitLabel = TextRef.Literal(unitsLabel),
                        decimalPlaces = if (uiState.isMgdl) 0 else 1,
                        expanded = bgExpanded,
                        onExpandedChange = {
                            bgExpanded = it
                            if (it) bgExpandedByUser = true
                        },
                        modifier = itemModifier.bringIntoViewRequester(bgExpandRequester),
                        // More than the number: how old the reading is, red and spoken when stale,
                        // and a plain "not available" when there is none.
                        valueContent = {
                            if (uiState.hasBgData) {
                                val bgFormat = if (uiState.isMgdl) NumberFormat.INTEGER else NumberFormat.DECIMAL_1
                                Text(
                                    text = stringResource(CoreUiStrings.value_with_unit, bgFormat.format(uiState.bg), unitsLabel),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val ageText = stringResource(CoreUiStrings.value_in_parentheses, stringResource(CoreUiStrings.format_mins, uiState.bgAgeMinutes))
                                // Old is only the red colour on screen; a screen reader says it.
                                val ageSpoken = if (bgIsOld) stringResource(UiStrings.a11y_bg_age_old, ageText, stringResource(UiStrings.a11y_bg_old_reading)) else ageText
                                Text(
                                    text = ageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (bgIsOld) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { contentDescription = ageSpoken }
                                )
                            } else {
                                Text(
                                    text = stringResource(CoreUiStrings.not_available_full),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )

                    // Percentage (collapsible)
                    val percentageExpandRequester = rememberBringIntoViewOnExpand(percentageExpanded)
                    ExpandableNumberInputRow(
                        labelRef = CoreUiStrings.wizard_use_percentage,
                        value = uiState.percentage.toDouble(),
                        onValueChange = onPercentageChange,
                        valueRange = Constants.WIZARD_PERCENTAGE_RANGE,
                        step = 5.0,
                        unitLabel = TextRef.Literal("%"),
                        decimalPlaces = 0,
                        expanded = percentageExpanded,
                        onExpandedChange = { percentageExpanded = it },
                        modifier = itemModifier.bringIntoViewRequester(percentageExpandRequester),
                        // The percent sign sits in its own format resource here, not after the number.
                        valueContent = {
                            Text(
                                text = stringResource(CoreUiStrings.format_percent, uiState.percentage),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )

                    // Profile (hidden in simple mode)
                    if (!uiState.simpleMode) {
                        ProfileRow(
                            profileNames = uiState.profileNames,
                            selectedIndex = uiState.selectedProfileIndex,
                            onSelect = onProfileSelect,
                            modifier = itemModifier
                        )
                    }
                    // Notes
                    if (uiState.showNotes) {
                        TextField(
                            value = uiState.notes,
                            onValueChange = onNotesChange,
                            label = { Text(stringResource(CoreUiStrings.notes_label)) },
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

/** Colours the place where [part] is in [text]. Does nothing when it is not there. */
private fun AnnotatedString.Builder.colorPart(text: String, part: String, color: Color) {
    val start = text.indexOf(part)
    if (start >= 0) addStyle(SpanStyle(color = color), start, start + part.length)
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
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        PreferenceSheetContent(
            settingsDef = settingsDef,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

/**
 * `Profile: name   (chevron)`, with the chevron in the same column as on the number rows above it.
 * A tap anywhere on the row opens the list of profiles, as wide as the row, and a tap on a name
 * picks it and closes the list. So a change is two taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileRow(
    profileNames: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = menuOpen,
        onExpandedChange = { menuOpen = it },
        modifier = modifier
    ) {
        // The whole row is the anchor: a tap on it opens the list, and the list takes its width.
        ExpandableRowHeader(
            label = stringResource(CoreUiStrings.profile),
            expanded = menuOpen,
            onExpand = { menuOpen = true },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                // One node for a screen reader ("Profile: name, drop-down list"). The expand and
                // collapse actions let it say whether the list is open.
                .semantics(mergeDescendants = true) {
                    if (menuOpen) collapse { menuOpen = false; true }
                    else expand { menuOpen = true; true }
                },
            // Only an icon, not a button: the row already takes the tap, and a second click
            // target inside it would toggle the list twice. Sized like the IconButton of the
            // number rows, so the chevrons line up.
            actions = {
                Box(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (menuOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }
        ) {
            Text(
                text = profileNames.getOrElse(selectedIndex) { "" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        ExposedDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            profileNames.forEachIndexed { index, name ->
                val isSelected = index == selectedIndex
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(index)
                        menuOpen = false
                    },
                    // The check is for the eye. A screen reader gets "selected" from the semantics.
                    trailingIcon = if (isSelected) {
                        { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.semantics { selected = isSelected }
                )
            }
        }
    }
}

/** What a carbs type does to the entered carbs, said on its button and shown in the info tooltip. */
@Composable
private fun carbsTypeEffect(type: CarbsType): String =
    if (type == CarbsType.BREAD) stringResource(UiStrings.wizard_carbs_type_bread_desc)
    else stringResource(
        UiStrings.wizard_carbs_type_desc,
        100 - type.carbsPercent,
        type.eCarbsPercent,
        type.eCarbsDelayMinutes,
        type.eCarbsDurationHours
    )
