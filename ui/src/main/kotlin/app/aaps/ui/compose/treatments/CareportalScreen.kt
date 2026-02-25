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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.MenuItemData
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.Ns
import app.aaps.ui.R
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.treatments.viewmodels.CareportalViewModel
import kotlinx.coroutines.launch

/**
 * Composable screen displaying therapy events (careportal entries) with delete and show hidden functionality.
 *
 * @param viewModel ViewModel managing state and business logic
 * @param persistenceLayer Database layer for therapy event data (needed for menu item)
 * @param translator Translator for therapy event types
 * @param setToolbarConfig Callback to set the toolbar configuration
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CareportalScreen(
    viewModel: CareportalViewModel,
    persistenceLayer: PersistenceLayer,
    translator: Translator,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit = { }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogMessage by remember { mutableStateOf("") }
    var showRemoveStartedDialog by remember { mutableStateOf(false) }

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
                },
                menuItems = listOf(
                    MenuItemData(
                        label = viewModel.rh.gs(R.string.careportal_remove_started_events),
                        onClick = {
                            showRemoveStartedDialog = true
                        }
                    )
                )
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

    // Remove started events dialog
    if (showRemoveStartedDialog) {
        OkCancelDialog(
            title = viewModel.rh.gs(app.aaps.core.ui.R.string.careportal),
            message = viewModel.rh.gs(R.string.careportal_remove_started_events),
            onConfirm = {
                scope.launch {
                    persistenceLayer.invalidateTherapyEventsWithNote(
                        viewModel.rh.gs(app.aaps.core.ui.R.string.androidaps_start),
                        Action.RESTART_EVENTS_REMOVED,
                        Sources.Treatments
                    )
                    viewModel.loadData()
                }
                showRemoveStartedDialog = false
            },
            onDismiss = { showRemoveStartedDialog = false }
        )
    }

    AapsTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ContentContainer(
                isLoading = uiState.isLoading,
                isEmpty = uiState.therapyEvents.isEmpty()
            ) {
                val haptic = LocalHapticFeedback.current

                TreatmentLazyColumn(
                    items = uiState.therapyEvents,
                    getTimestamp = { it.timestamp },
                    getItemKey = { it.id },
                    rh = viewModel.rh,
                    itemContent = { te ->
                        TherapyEventItem(
                            therapyEvent = te,
                            isRemovingMode = uiState.isRemovingMode,
                            isSelected = te in uiState.selectedItems,
                            onClick = {
                                if (uiState.isRemovingMode && te.isValid) {
                                    // Haptic feedback for selection toggle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Toggle selection
                                    viewModel.toggleSelection(te)
                                }
                            },
                            onLongPress = {
                                if (te.isValid && !uiState.isRemovingMode) {
                                    // Haptic feedback for selection mode entry
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Enter selection mode and select this item
                                    viewModel.enterSelectionMode(te)
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
private fun TherapyEventItem(
    therapyEvent: TE,
    isRemovingMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    rh: ResourceHelper,
    translator: Translator
) {
    val profileUtil = LocalProfileUtil.current
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
            // Time and Type row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 3.dp, end = 8.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time
                Text(
                    text = dateUtil.timeString(therapyEvent.timestamp),
                    fontSize = 14.sp
                )

                // Event type
                Text(
                    text = translator.translate(therapyEvent.type),
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Spacer
                Box(modifier = Modifier.weight(1f))

                // NS indicator
                if (therapyEvent.ids.nightscoutId != null) {
                    Icon(
                        imageVector = Ns,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                        modifier = Modifier
                            .size(21.dp)
                            .padding(end = 5.dp)
                    )
                }

                // Invalid indicator
                if (!therapyEvent.isValid) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                        modifier = Modifier
                            .size(21.dp)
                            .padding(start = 5.dp),
                        tint = Color(AapsTheme.generalColors.invalidatedRecord.value)
                    )
                }
            }

            // Duration, BG, and Note row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 1.dp, end = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Duration
                if (therapyEvent.duration != 0L) {
                    Text(
                        text = dateUtil.niceTimeScalar(therapyEvent.duration, rh),
                        modifier = Modifier.padding(start = 16.dp),
                        fontSize = 12.sp
                    )
                }

                // BG value (for FINGER_STICK_BG_VALUE)
                if (therapyEvent.type == TE.Type.FINGER_STICK_BG_VALUE && therapyEvent.glucose != null) {
                    Text(
                        text = profileUtil.stringInCurrentUnitsDetect(therapyEvent.glucose!!),
                        modifier = Modifier.padding(start = 16.dp),
                        fontSize = 12.sp
                    )
                }

                // Note
                if (!therapyEvent.note.isNullOrEmpty()) {
                    Text(
                        text = therapyEvent.note!!,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f),
                        fontSize = 12.sp
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }

                // Checkbox for removal
                if (isRemovingMode && therapyEvent.isValid) {
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
