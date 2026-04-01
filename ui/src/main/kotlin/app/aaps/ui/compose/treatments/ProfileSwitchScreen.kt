package app.aaps.ui.compose.treatments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.Ns
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.ui.R
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.treatments.viewmodels.ProfileSwitchViewModel

/**
 * Composable screen displaying profile switches with delete and show hidden functionality.
 *
 * @param viewModel ViewModel managing state and business logic
 * @param localProfileManager Profile manager for profile operations
 * @param decimalFormatter Formatter for decimal values
 * @param uel User entry logger
 * @param setToolbarConfig Callback to set the toolbar configuration
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileSwitchScreen(
    viewModel: ProfileSwitchViewModel,
    localProfileManager: LocalProfileManager,
    decimalFormatter: DecimalFormatter,
    uel: UserEntryLogger,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit = { }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogMessage by remember { mutableStateOf("") }
    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneDialogMessage by remember { mutableStateOf("") }
    var pendingCloneAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val currentlyActiveProfile = remember(uiState.profileSwitches) {
        viewModel.getActiveProfile()
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

    // Clone confirmation dialog
    if (showCloneDialog) {
        OkCancelDialog(
            title = viewModel.rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch),
            message = cloneDialogMessage,
            onConfirm = {
                pendingCloneAction?.invoke()
                pendingCloneAction = null
                showCloneDialog = false
            },
            onDismiss = {
                pendingCloneAction = null
                showCloneDialog = false
            }
        )
    }

    AapsTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ContentContainer(
                isLoading = uiState.isLoading,
                isEmpty = uiState.profileSwitches.isEmpty()
            ) {
                val haptic = LocalHapticFeedback.current

                TreatmentLazyColumn(
                    items = uiState.profileSwitches,
                    getTimestamp = { it.timestamp },
                    getItemKey = { if (it is ProfileSealed.EPS) "eps_${it.id}" else "ps_${it.id}" },
                    rh = viewModel.rh,
                    itemContent = { profileSwitch ->
                        ProfileSwitchItem(
                            profileSwitch = profileSwitch,
                            isActive = profileSwitch.id == currentlyActiveProfile?.id,
                            isFuture = profileSwitch.timestamp > viewModel.dateUtil.now(),
                            isRemovingMode = uiState.isRemovingMode,
                            isSelected = profileSwitch in uiState.selectedItems,
                            onClick = {
                                if (uiState.isRemovingMode && profileSwitch is ProfileSealed.PS && profileSwitch.isValid) {
                                    // Haptic feedback for selection toggle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Toggle selection
                                    viewModel.toggleSelection(profileSwitch)
                                }
                            },
                            onLongPress = {
                                if (profileSwitch is ProfileSealed.PS && profileSwitch.isValid && !uiState.isRemovingMode) {
                                    // Haptic feedback for selection mode entry
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Enter selection mode and select this item
                                    viewModel.enterSelectionMode(profileSwitch)
                                }
                            },
                            onClone = { ps ->
                                val profileName = ps.value.getCustomizedName(decimalFormatter)
                                val timestamp = ps.value.timestamp
                                val timestampStr = viewModel.dateUtil.dateAndTimeString(timestamp)

                                cloneDialogMessage = "${viewModel.rh.gs(app.aaps.core.ui.R.string.copytolocalprofile)}\n$profileName\n$timestampStr"
                                pendingCloneAction = {
                                    uel.log(
                                        action = Action.PROFILE_SWITCH_CLONED,
                                        source = Sources.Treatments,
                                        note = "$profileName ${timestampStr.replace(".", "_")}",
                                        listValues = listOf(
                                            ValueWithUnit.Timestamp(timestamp),
                                            ValueWithUnit.SimpleString(ps.value.profileName)
                                        )
                                    )
                                    val nonCustomized = ps.convertToNonCustomizedProfile(viewModel.dateUtil)
                                    localProfileManager.addProfile(
                                        localProfileManager.copyFrom(
                                            nonCustomized,
                                            "$profileName ${timestampStr.replace(".", "_")}"
                                        )
                                    )
                                }
                                showCloneDialog = true
                            },
                            rh = viewModel.rh,
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
private fun ProfileSwitchItem(
    profileSwitch: ProfileSealed,
    isActive: Boolean,
    isFuture: Boolean,
    isRemovingMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onClone: (ProfileSealed.PS) -> Unit,
    rh: ResourceHelper,
    decimalFormatter: DecimalFormatter
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
        // Single row with all info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time, profile name, duration, percentage, timeshift - all in one compact format
            val profileName = when (profileSwitch) {
                is ProfileSealed.PS -> profileSwitch.value.getCustomizedName(decimalFormatter)
                is ProfileSealed.EPS -> profileSwitch.value.originalCustomizedName
                else -> profileSwitch.profileName
            }

            Text(
                text = buildString {
                    // Time
                    append(dateUtil.timeString(profileSwitch.timestamp))
                    append(" ")
                    // Profile name
                    append(profileName)
                    // Duration
                    if (profileSwitch.duration != null && profileSwitch.duration != 0L) {
                        append(" ")
                        append(T.msecs(profileSwitch.duration ?: 0L).mins().toInt())
                        append(rh.gs(app.aaps.core.keys.R.string.units_min))
                    }
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
            if (!profileSwitch.isValid) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp),
                    tint = Color(AapsTheme.generalColors.invalidatedRecord.value)
                )
            }

            // Clone button - only for PS
            if (!isRemovingMode && profileSwitch is ProfileSealed.PS && profileSwitch.isValid) {
                Text(
                    text = stringResource(R.string.clone_label),
                    modifier = Modifier
                        .clickable {
                            onClone(profileSwitch)
                        }
                        .padding(start = 5.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            }

            // Pump indicator - only for EPS (all EPS, not just those with pumpId)
            if (profileSwitch is ProfileSealed.EPS) {
                Icon(
                    imageVector = Pump,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.pump_history),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp)
                )
            }

            // NS indicator - for both EPS and PS
            if (profileSwitch.ids?.nightscoutId != null) {
                Icon(
                    imageVector = Ns,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp)
                )
            }

            // Checkbox for removal
            if (isRemovingMode && profileSwitch is ProfileSealed.PS && profileSwitch.isValid) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
