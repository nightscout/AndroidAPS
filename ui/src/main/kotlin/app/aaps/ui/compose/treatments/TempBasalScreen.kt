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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.Ns
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.ui.R
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.treatments.viewmodels.TempBasalViewModel
import kotlinx.coroutines.launch

/**
 * Composable screen displaying temporary basals and extended boluses with delete and show hidden functionality.
 *
 * @param viewModel ViewModel managing state and business logic
 * @param profileFunction Profile function for IOB calculations in item
 * @param activePlugin Active plugin for IOB calculations in item
 * @param setToolbarConfig Callback to set the toolbar configuration
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TempBasalScreen(
    viewModel: TempBasalViewModel,
    profileFunction: ProfileFunction,
    activePlugin: ActivePlugin,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit = { }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogMessage by remember { mutableStateOf("") }

    // Update toolbar configuration whenever state changes
    LaunchedEffect(uiState.isRemovingMode, uiState.selectedItems.size, uiState.showInvalidated) {
        setToolbarConfig(
            viewModel.getToolbarConfig(
                onNavigateBack = onNavigateBack,
                onDeleteClick = {
                    if (uiState.selectedItems.isNotEmpty()) {
                        scope.launch {
                            deleteDialogMessage = viewModel.getDeleteConfirmationMessage()
                            showDeleteDialog = true
                        }
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
                isEmpty = uiState.tempBasals.isEmpty()
            ) {
                val haptic = LocalHapticFeedback.current

                TreatmentLazyColumn(
                    items = uiState.tempBasals,
                    getTimestamp = { it.timestamp },
                    getItemKey = { it.id },
                    rh = viewModel.rh,
                    itemContent = { tb ->
                        TempBasalItem(
                            tempBasal = tb,
                            isActive = tb.isInProgress,
                            isFuture = tb.timestamp > viewModel.dateUtil.now(),
                            isRemovingMode = uiState.isRemovingMode,
                            isSelected = tb in uiState.selectedItems,
                            onClick = {
                                if (uiState.isRemovingMode && tb.isValid) {
                                    // Haptic feedback for selection toggle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Toggle selection
                                    viewModel.toggleSelection(tb)
                                }
                            },
                            onLongPress = {
                                if (tb.isValid && !uiState.isRemovingMode) {
                                    // Haptic feedback for selection mode entry
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Enter selection mode and select this item
                                    viewModel.enterSelectionMode(tb)
                                }
                            },
                            profileFunction = profileFunction
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
private fun TempBasalItem(
    tempBasal: TB,
    isActive: Boolean,
    isFuture: Boolean,
    isRemovingMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    profileFunction: ProfileFunction
) {
    val dateUtil = LocalDateUtil.current
    val now = dateUtil.now()
    val profile by produceState<app.aaps.core.interfaces.profile.EffectiveProfile?>(null, now) {
        value = profileFunction.getProfile(now)
    }
    val iob = if (profile != null) {
        tempBasal.iobCalc(now, profile!!)
    } else {
        IobTotal(now)
    }

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
            // Time range, rate, IOB, duration - all in one compact format
            Text(
                text = buildAnnotatedString {
                    // Time range
                    append(dateUtil.timeRangeString(tempBasal.timestamp, tempBasal.end))
                    append(" ")
                    // Rate
                    if (tempBasal.isAbsolute) {
                        append(stringResource(app.aaps.core.ui.R.string.pump_base_basal_rate, tempBasal.rate))
                    } else {
                        append(stringResource(app.aaps.core.ui.R.string.format_percent, tempBasal.rate.toInt()))
                    }
                    // IOB in color
                    if (iob.basaliob != 0.0) {
                        append(" ")
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = Color(AapsTheme.generalColors.activeInsulinText.value)
                            )
                        ) {
                            append("(")
                            append(stringResource(app.aaps.core.ui.R.string.format_insulin_units, iob.basaliob))
                            append(")")
                        }
                    }
                    append(" ")
                    // Duration
                    append(T.msecs(tempBasal.duration).mins().toInt().toString())
                    append(stringResource(app.aaps.core.keys.R.string.units_min))
                },
                modifier = Modifier.padding(start = 4.dp),
                fontSize = 14.sp,
                color = when {
                    isActive -> Color(AapsTheme.generalColors.activeInsulinText.value)
                    isFuture -> Color(AapsTheme.generalColors.futureRecord.value)
                    else     -> MaterialTheme.colorScheme.onSurface
                }
            )

            // Type flags
            if (tempBasal.type == TB.Type.FAKE_EXTENDED) {
                Text(
                    text = stringResource(R.string.tbr_type_flag_extended),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp,
                    color = ElementType.TEMP_BASAL.color()
                )
            }

            if (tempBasal.type == TB.Type.PUMP_SUSPEND) {
                Text(
                    text = stringResource(R.string.tbr_type_flag_suspended),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp,
                    color = ElementType.TEMP_BASAL.color()
                )
            }

            if (tempBasal.type == TB.Type.EMULATED_PUMP_SUSPEND) {
                Text(
                    text = stringResource(R.string.tbr_type_flag_emulated_suspended),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp,
                    color = ElementType.TEMP_BASAL.color()
                )
            }

            if (tempBasal.type == TB.Type.SUPERBOLUS) {
                Text(
                    text = stringResource(R.string.tbr_type_flag_superbolus),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp,
                    color = ElementType.TEMP_BASAL.color()
                )
            }

            // Spacer
            Box(modifier = Modifier.weight(1f))

            // Invalid indicator
            if (!tempBasal.isValid) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp),
                    tint = Color(AapsTheme.generalColors.invalidatedRecord.value)
                )
            }

            // PH indicator (Pump History)
            if (tempBasal.ids.pumpId != null) {
                Icon(
                    imageVector = Pump,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.pump_history),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp)
                )
            }

            // NS indicator
            if (tempBasal.ids.nightscoutId != null) {
                Icon(
                    imageVector = Ns,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp)
                )
            }

            // Checkbox for removal
            if (isRemovingMode && tempBasal.isValid) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
