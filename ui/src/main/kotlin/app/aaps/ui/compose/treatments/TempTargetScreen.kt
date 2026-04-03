package app.aaps.ui.compose.treatments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.objects.extensions.highValueToUnitsToString
import app.aaps.core.objects.extensions.lowValueToUnitsToString
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.Ns
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.treatments.viewmodels.TempTargetViewModel

/**
 * Composable screen displaying temporary targets with delete and show hidden functionality.
 *
 * @param viewModel ViewModel managing state and business logic
 * @param translator Translator for temp target reasons
 * @param decimalFormatter Formatter for decimal values
 * @param setToolbarConfig Callback to set the toolbar configuration
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TempTargetScreen(
    viewModel: TempTargetViewModel,
    translator: Translator,
    decimalFormatter: DecimalFormatter,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit = { }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogMessage by remember { mutableStateOf("") }

    val currentlyActiveTarget = remember(uiState.tempTargets) {
        viewModel.getActiveTarget()
    }

    // Update toolbar configuration whenever state changes
    LaunchedEffect(uiState.isRemovingMode, uiState.selectedItems.size, uiState.showInvalidated) {
        setToolbarConfig(
            viewModel.getToolbarConfig(
                onNavigateBack = onNavigateBack,
                onDeleteClick = {
                    if (uiState.selectedItems.isNotEmpty()) {
                        deleteDialogMessage = viewModel.getDeleteConfirmationMessage()
                        showDeleteDialog = true
                    }
                }
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
                isEmpty = uiState.tempTargets.isEmpty()
            ) {
                val haptic = LocalHapticFeedback.current

                TreatmentLazyColumn(
                    items = uiState.tempTargets,
                    getTimestamp = { it.timestamp },
                    getItemKey = { it.id },
                    rh = viewModel.rh,
                    itemContent = { tt ->
                        TempTargetItem(
                            tempTarget = tt,
                            isActive = tt.id == currentlyActiveTarget?.id,
                            isFuture = tt.timestamp > viewModel.dateUtil.now(),
                            isRemovingMode = uiState.isRemovingMode,
                            isSelected = tt in uiState.selectedItems,
                            onClick = {
                                if (uiState.isRemovingMode && tt.isValid) {
                                    // Haptic feedback for selection toggle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Toggle selection
                                    viewModel.toggleSelection(tt)
                                }
                            },
                            onLongPress = {
                                if (tt.isValid && !uiState.isRemovingMode) {
                                    // Haptic feedback for selection mode entry
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Enter selection mode and select this item
                                    viewModel.enterSelectionMode(tt)
                                }
                            },
                            rh = viewModel.rh,
                            translator = translator,
                            decimalFormatter = decimalFormatter
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TempTargetItem(
    tempTarget: TT,
    isActive: Boolean,
    isFuture: Boolean,
    isRemovingMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    rh: ResourceHelper,
    translator: Translator,
    decimalFormatter: DecimalFormatter
) {
    val profileUtil = LocalProfileUtil.current
    val dateUtil = LocalDateUtil.current
    val units = profileUtil.units

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
        // Single row with all info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time range, targets, duration, reason - all in one compact format
            Text(
                text = buildString {
                    // Time range
                    append(dateUtil.timeRangeString(tempTarget.timestamp, tempTarget.end))
                    append(" ")
                    // Targets - show only one value if low and high are the same
                    if (tempTarget.lowTarget == tempTarget.highTarget) {
                        append(tempTarget.lowValueToUnitsToString(units, decimalFormatter))
                    } else {
                        append(tempTarget.lowValueToUnitsToString(units, decimalFormatter))
                        append("-")
                        append(tempTarget.highValueToUnitsToString(units, decimalFormatter))
                    }
                    append(" ")
                    // Duration
                    append(T.msecs(tempTarget.duration).mins().toInt())
                    append(rh.gs(app.aaps.core.keys.R.string.units_min))
                    append(" ")
                    // Reason (without "Reason:" label)
                    append(translator.translate(tempTarget.reason))
                },
                modifier = Modifier.padding(start = 4.dp),
                fontSize = 14.sp,
                color = when {
                    isActive -> Color(AapsTheme.generalColors.activeInsulinText.value)
                    isFuture -> Color(AapsTheme.generalColors.futureRecord.value)
                    else     -> MaterialTheme.colorScheme.onSurface
                }
            )

            // Spacer
            Box(modifier = Modifier.weight(1f))

            // Invalid indicator
            if (!tempTarget.isValid) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp),
                    tint = Color(AapsTheme.generalColors.invalidatedRecord.value)
                )
            }

            // NS indicator
            if (tempTarget.ids.nightscoutId != null) {
                Icon(
                    imageVector = Ns,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp)
                )
            }

            // Checkbox for removal
            if (isRemovingMode && tempTarget.isValid) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
