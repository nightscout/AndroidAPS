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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.Ns
import app.aaps.ui.R
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.treatments.viewmodels.RunningModeViewModel

/**
 * Composable screen displaying running modes (offline events) with delete and show hidden functionality.
 *
 * @param viewModel ViewModel managing state and business logic
 * @param translator Translator for running mode names
 * @param setToolbarConfig Callback to set the toolbar configuration
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RunningModeScreen(
    viewModel: RunningModeViewModel,
    translator: Translator,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit = { }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogMessage by remember { mutableStateOf("") }

    val currentlyActiveMode = remember(uiState.runningModes) {
        viewModel.getActiveMode()
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
                isEmpty = uiState.runningModes.isEmpty()
            ) {
                val haptic = LocalHapticFeedback.current

                TreatmentLazyColumn(
                    items = uiState.runningModes,
                    getTimestamp = { it.timestamp },
                    getItemKey = { it.id },
                    rh = viewModel.rh,
                    itemContent = { rm ->
                        RunningModeItem(
                            runningMode = rm,
                            isActive = rm.id == currentlyActiveMode.id,
                            isFuture = rm.timestamp > viewModel.dateUtil.now(),
                            isRemovingMode = uiState.isRemovingMode,
                            isSelected = rm in uiState.selectedItems,
                            onClick = {
                                if (uiState.isRemovingMode && rm.isValid) {
                                    // Haptic feedback for selection toggle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Toggle selection
                                    viewModel.toggleSelection(rm)
                                }
                            },
                            onLongPress = {
                                if (rm.isValid && !uiState.isRemovingMode) {
                                    // Haptic feedback for selection mode entry
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Enter selection mode and select this item
                                    viewModel.enterSelectionMode(rm)
                                }
                            },
                            rh = viewModel.rh,
                            translator = translator
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
private fun RunningModeItem(
    runningMode: RM,
    isActive: Boolean,
    isFuture: Boolean,
    isRemovingMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    rh: ResourceHelper,
    translator: Translator
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
            // Main content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time
                Text(
                    text = dateUtil.timeString(runningMode.timestamp),
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 14.sp,
                    color = when {
                        isActive -> Color(AapsTheme.generalColors.activeInsulinText.value)
                        isFuture -> Color(AapsTheme.generalColors.futureRecord.value)
                        else     -> MaterialTheme.colorScheme.onSurface
                    }
                )

                // Mode
                Text(
                    text = translator.translate(runningMode.mode),
                    modifier = Modifier.padding(start = 10.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Duration
                Text(
                    text = if (runningMode.duration > T.months(12).msecs()) {
                        rh.gs(R.string.until_changed)
                    } else if (runningMode.isTemporary()) {
                        rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(runningMode.duration).mins())
                    } else {
                        ""
                    },
                    modifier = Modifier.padding(start = 10.dp),
                    fontSize = 14.sp
                )

                // Spacer
                Box(modifier = Modifier.weight(1f))

                // NS indicator
                if (runningMode.ids.nightscoutId != null) {
                    Icon(
                        imageVector = Ns,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                        modifier = Modifier
                            .size(21.dp)
                            .padding(start = 5.dp)
                    )
                }

                // Invalid indicator
                if (!runningMode.isValid) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                        modifier = Modifier
                            .size(21.dp)
                            .padding(start = 5.dp),
                        tint = Color(AapsTheme.generalColors.invalidatedRecord.value)
                    )
                }

                // Checkbox for removal
                if (isRemovingMode && runningMode.isValid) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
