package app.aaps.ui.compose.treatments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.icons.Ns
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.treatments.viewmodels.BolusCarbsViewModel

/**
 * Composable screen displaying boluses and carbs in a combined list.
 *
 * @param viewModel ViewModel managing state and business logic
 * @param activePlugin Active plugin for IOB calculations
 * @param setToolbarConfig Lambda to set toolbar configuration
 * @param onNavigateBack Lambda to handle back navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BolusCarbsScreen(
    viewModel: BolusCarbsViewModel,
    activePlugin: ActivePlugin,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit = { }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogMessage by remember { mutableStateOf("") }

    val profile = remember(uiState.mealLinks) { viewModel.getProfile() }

    // Update toolbar configuration whenever state changes
    LaunchedEffect(uiState.isRemovingMode, uiState.selectedItems.size, uiState.showInvalidated) {
        setToolbarConfig(
            SelectableListToolbar(
                isRemovingMode = uiState.isRemovingMode,
                selectedCount = uiState.selectedItems.size,
                onExitRemovingMode = { viewModel.exitSelectionMode() },
                onNavigateBack = onNavigateBack,
                onDelete = {
                    if (uiState.selectedItems.isNotEmpty()) {
                        deleteDialogMessage = viewModel.getDeleteConfirmationMessage()
                        showDeleteDialog = true
                    }
                },
                rh = viewModel.rh,
                showInvalidated = uiState.showInvalidated,
                onToggleInvalidated = { viewModel.toggleInvalidated() }
            )
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        OkCancelDialog(
            title = viewModel.rh.gs(app.aaps.core.ui.R.string.removerecord),
            message = deleteDialogMessage,
            onConfirm = {
                viewModel.deleteSelected()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    AapsTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ContentContainer(
                isLoading = uiState.isLoading,
                isEmpty = uiState.mealLinks.isEmpty()
            ) {
                val haptic = LocalHapticFeedback.current

                TreatmentLazyColumn(
                    items = uiState.mealLinks,
                    getTimestamp = { it.bolusCalculatorResult?.timestamp ?: it.bolus?.timestamp ?: it.carbs?.timestamp ?: 0L },
                    getItemKey = { "b${it.bolus?.id}_c${it.carbs?.id}_bcr${it.bolusCalculatorResult?.id}" },
                    rh = viewModel.rh,
                    itemContent = { ml ->
                        MealLinkItem(
                            mealLink = ml,
                            isRemovingMode = uiState.isRemovingMode,
                            isSelected = ml in uiState.selectedItems,
                            onClick = {
                                if (uiState.isRemovingMode && ml.isValid()) {
                                    // Haptic feedback for selection toggle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Toggle selection
                                    viewModel.toggleSelection(ml)
                                }
                            },
                            onLongPress = {
                                if (ml.isValid() && !uiState.isRemovingMode) {
                                    // Haptic feedback for selection mode entry
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Enter selection mode and select this item
                                    viewModel.enterSelectionMode(ml)
                                }
                            },
                            profile = profile,
                            activePlugin = activePlugin,
                            rh = viewModel.rh,
                            decimalFormatter = viewModel.decimalFormatter,
                            showInvalidated = uiState.showInvalidated
                        )
                    }
                )
            }

            // Error display
            AapsSnackbarHost(
                message = uiState.snackbarMessage,
                onDismiss = { viewModel.clearSnackbar() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

data class MealLink(
    val bolus: BS? = null,
    val carbs: CA? = null,
    val bolusCalculatorResult: BCR? = null
) {

    fun isValid(): Boolean {
        return (bolus?.isValid ?: true) && (carbs?.isValid ?: true) && (bolusCalculatorResult?.isValid ?: true)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealLinkItem(
    mealLink: MealLink,
    isRemovingMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    profile: Profile?,
    activePlugin: ActivePlugin,
    rh: ResourceHelper,
    decimalFormatter: DecimalFormatter,
    showInvalidated: Boolean
) {
    val dateUtil = LocalDateUtil.current
    AapsCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        selected = isSelected
    ) {
        Column(
            modifier = Modifier.padding(1.dp)
        ) {

            // Bolus Calculator Result (Metadata)
            mealLink.bolusCalculatorResult?.let { bcr ->
                if (bcr.isValid || showInvalidated) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateUtil.timeString(bcr.timestamp),
                            fontSize = 14.sp
                        )

                        Box(modifier = Modifier.weight(1f))

                        Icon(
                            imageVector = IcCalculator,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.boluswizard),
                            modifier = Modifier.size(21.dp),
                            tint = Color(AapsTheme.generalColors.calculator.value)
                        )

                        if (bcr.ids.nightscoutId != null) {
                            Icon(
                                imageVector = Ns,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                                modifier = Modifier
                                    .size(21.dp)
                                    .padding(start = 5.dp)
                            )
                        }

                        if (isRemovingMode && bcr.isValid) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onClick() },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Bolus
            mealLink.bolus?.let { bolus ->
                if (bolus.isValid || showInvalidated) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateUtil.timeString(bolus.timestamp),
                            fontSize = 14.sp,
                            color = if (bolus.timestamp > dateUtil.now()) Color(AapsTheme.generalColors.futureRecord.value) else MaterialTheme.colorScheme.onSurface
                        )

                        // Bolus amount with IOB
                        profile?.let { prof ->
                            val iob = bolus.iobCalc(activePlugin, System.currentTimeMillis(), prof.dia)
                            val bolusText = if (iob.iobContrib > 0.01) {
                                buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(stringResource(app.aaps.core.ui.R.string.format_insulin_units, bolus.amount))
                                        append(" ")
                                    }
                                    withStyle(
                                        style = SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(AapsTheme.generalColors.activeInsulinText.value)
                                        )
                                    ) {
                                        append("(")
                                        append(stringResource(app.aaps.core.ui.R.string.format_insulin_units, iob.iobContrib))
                                        append(")")
                                    }
                                }
                            } else {
                                buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(stringResource(app.aaps.core.ui.R.string.format_insulin_units, bolus.amount))
                                    }
                                }
                            }

                            Text(
                                text = bolusText,
                                modifier = Modifier.padding(start = 10.dp),
                                fontSize = 14.sp
                            )
                        } ?: run {
                            Text(
                                text = stringResource(app.aaps.core.ui.R.string.format_insulin_units, bolus.amount),
                                modifier = Modifier.padding(start = 10.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(modifier = Modifier.weight(1f))

                        // Bolus type
                        when (bolus.type) {
                            BS.Type.SMB     -> {
                                Icon(
                                    imageVector = IcSmb,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.smb_shortname),
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            BS.Type.NORMAL  -> {
                                Icon(
                                    imageVector = IcCarbs,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.careportal_mealbolus),
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            BS.Type.PRIMING -> {
                                Icon(
                                    imageVector = IcPumpCartridge,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.prime_fill),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }

                        if (bolus.ids.nightscoutId != null) {
                            Icon(
                                imageVector = Ns,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                                modifier = Modifier
                                    .size(21.dp)
                                    .padding(start = 5.dp)
                            )
                        }

                        if (bolus.ids.isPumpHistory()) {
                            Icon(
                                imageVector = Pump,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.pump_history),
                                modifier = Modifier
                                    .size(21.dp)
                                    .padding(start = 5.dp)
                            )
                        }

                        if (!bolus.isValid) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                                modifier = Modifier
                                    .size(21.dp)
                                    .padding(start = 5.dp),
                                tint = Color(AapsTheme.generalColors.invalidatedRecord.value)
                            )
                        }

                        if (isRemovingMode && bolus.isValid) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onClick() },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Carbs
            mealLink.carbs?.let { carbs ->
                if (carbs.isValid || showInvalidated) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateUtil.timeString(carbs.timestamp),
                            fontSize = 14.sp
                        )

                        Text(
                            text = rh.gs(app.aaps.core.ui.R.string.carbs) + ":",
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                            fontSize = 14.sp
                        )

                        Text(
                            text = rh.gs(app.aaps.core.objects.R.string.format_carbs, carbs.amount.toInt()),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (carbs.duration > 0) {
                            Text(
                                text = rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(carbs.duration).mins().toInt()),
                                modifier = Modifier.padding(start = 8.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        carbs.notes?.takeIf { it.isNotEmpty() }?.let { notes ->
                            Text(
                                text = notes,
                                modifier = Modifier.padding(start = 8.dp),
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Box(modifier = Modifier.weight(1f))

                        if (carbs.ids.nightscoutId != null) {
                            Icon(
                                imageVector = Ns,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                                modifier = Modifier
                                    .size(21.dp)
                                    .padding(start = 5.dp)
                            )
                        }

                        if (carbs.ids.isPumpHistory()) {
                            Icon(
                                imageVector = Pump,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.pump_history),
                                modifier = Modifier
                                    .size(21.dp)
                                    .padding(start = 5.dp)
                            )
                        }

                        if (!carbs.isValid) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                                modifier = Modifier
                                    .size(21.dp)
                                    .padding(start = 5.dp),
                                tint = Color(AapsTheme.generalColors.invalidatedRecord.value)
                            )
                        }

                        if (isRemovingMode && carbs.isValid) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onClick() },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Bolus notes (carbs notes are shown inline)
            mealLink.bolus?.let { bolus ->
                bolus.notes?.takeIf { it.isNotEmpty() && mealLink.carbs == null }?.let { notes ->
                    Text(
                        text = notes,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 3.dp),
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
