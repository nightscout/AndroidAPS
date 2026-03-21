package app.aaps.ui.compose.siteRotationDialog

import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.compose.siteRotation.ArrowSelectionDialog
import app.aaps.core.ui.compose.siteRotation.SiteEntryDisplayData
import app.aaps.core.ui.compose.siteRotation.SiteEntryList
import app.aaps.core.ui.compose.siteRotation.ZoomableBodyDiagram
import app.aaps.core.ui.compose.siteRotation.directionToComposeIcon
import app.aaps.ui.R
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationUiState
import kotlinx.coroutines.launch
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRotationManagementScreen(
    viewModel: SiteRotationManagementViewModel,
    onClose: () -> Unit,
    onPreferenceClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? AppCompatActivity

    val displayEntries = remember(uiState.filteredEntries) {
        viewModel.formatDisplayEntries(uiState.filteredEntries)
    }

    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Arrow selection dialog
    var showArrowDialog by rememberSaveable { mutableStateOf(false) }
    if (showArrowDialog) {
        ArrowSelectionDialog(
            onDismiss = { showArrowDialog = false },
            onArrowSelected = { arrow ->
                viewModel.updateEditArrow(arrow)
                showArrowDialog = false
            }
        )
    }

    // Confirmation dialog
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    if (showConfirmation) {
        if (!uiState.isEdited || uiState.editedTe == null) {
            showConfirmation = false
        } else {
            val summaryLines = viewModel.buildConfirmationSummary()
            OkCancelDialog(
                title = stringResource(R.string.update_site_change),
                message = summaryLines.joinToString("<br/>"),
                icon = when (uiState.editedTe?.type) {
                    TE.Type.CANNULA_CHANGE -> IcCannulaChange
                    TE.Type.SENSOR_CHANGE  -> IcCgmInsert
                    else                   -> IcSiteRotation
                },
                iconTint = AapsTheme.elementColors.tempBasal,
                onConfirm = {
                    viewModel.confirmAndSave()
                    showConfirmation = false
                },
                onDismiss = { showConfirmation = false }
            )
        }
    }

    SiteRotationManagementContent(
        uiState = uiState,
        displayEntries = displayEntries,
        onClose = onClose,
        onPreferenceClick = onPreferenceClick,
        onShowPumpSites = { viewModel.setShowPumpSites(it) },
        onShowCgmSites = { viewModel.setShowCgmSites(it) },
        onZoneClick = { viewModel.onZoneClick(it) },
        onEntryClick = { viewModel.onZoneClick(it.location) },
        onEditEntry = { viewModel.startEditing(it) },
        onCancelEdit = { viewModel.cancelEditing() },
        onConfirmEdit = { showConfirmation = true },
        onArrowClick = { showArrowDialog = true },
        onNoteChange = { viewModel.updateEditNote(it) },
        editedTeDate = uiState.editedTe?.let { viewModel.formatDate(it.timestamp) } ?: "",
        editedTeLocation = uiState.editedTe?.let { viewModel.formatLocation(it.location) } ?: ""
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteRotationManagementContent(
    uiState: SiteRotationUiState,
    displayEntries: List<SiteEntryDisplayData>,
    onClose: () -> Unit,
    onPreferenceClick: () -> Unit,
    onShowPumpSites: (Boolean) -> Unit,
    onShowCgmSites: (Boolean) -> Unit,
    onZoneClick: (TE.Location) -> Unit,
    onEntryClick: (SiteEntryDisplayData) -> Unit,
    onEditEntry: (Long) -> Unit,
    onCancelEdit: () -> Unit,
    onConfirmEdit: () -> Unit,
    onArrowClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    editedTeDate: String,
    editedTeLocation: String
) {
    val focusManager = LocalFocusManager.current
    val isEditing = uiState.editedTe != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IcSiteRotation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(AapsSpacing.medium))
                        Text(stringResource(if (isEditing) R.string.edit_site else CoreUiR.string.site_rotation))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) onCancelEdit() else onClose()
                    }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) stringResource(CoreUiR.string.cancel) else stringResource(CoreUiR.string.back)
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = onConfirmEdit) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(CoreUiR.string.save),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = onPreferenceClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(CoreUiR.string.settings)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .clearFocusOnTap(focusManager)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiChoiceSegmentedButtonRow(
                        modifier = Modifier.weight(1f)
                    ) {
                        val isEditingPump = isEditing && uiState.editedTe?.type == TE.Type.CANNULA_CHANGE
                        SegmentedButton(
                            checked = if (isEditingPump) true else uiState.showPumpSites,
                            onCheckedChange = { onShowPumpSites(!uiState.showPumpSites) },
                            enabled = !isEditingPump,
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {}
                        ) {
                            Icon(
                                imageVector = IcCannulaChange,
                                contentDescription = stringResource(CoreUiR.string.careportal_pump_site_management),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        val isEditingCgm = isEditing && uiState.editedTe?.type == TE.Type.SENSOR_CHANGE
                        SegmentedButton(
                            checked = if (isEditingCgm) true else uiState.showCgmSites,
                            onCheckedChange = { onShowCgmSites(!uiState.showCgmSites) },
                            enabled = !isEditingCgm,
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {}
                        ) {
                            Icon(
                                imageVector = IcCgmInsert,
                                contentDescription = stringResource(CoreUiR.string.careportal_cgm_site_management),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    val tooltipState = remember { TooltipState() }
                    val scope = rememberCoroutineScope()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(R.string.site_filter_info))
                            }
                        },
                        state = tooltipState
                    ) {
                        IconButton(
                            onClick = { scope.launch { tooltipState.show() } },
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

                Box(
                    modifier = Modifier
                        .weight(2f * uiState.showBodyType.sizeRatio)
                        .fillMaxWidth()
                ) {
                    ZoomableBodyDiagram(
                        filteredLocationColor = uiState.filteredLocationColor,
                        showPumpSites = uiState.showPumpSites,
                        showCgmSites = uiState.showCgmSites,
                        selectedLocation = uiState.selectedLocation,
                        bodyType = uiState.showBodyType,
                        onZoneClick = onZoneClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AapsSpacing.extraLarge),
                        editedType = uiState.editedTe?.type
                    )
                }

                SiteEntryList(
                    entries = displayEntries,
                    showEditButton = true,
                    onEntryClick = onEntryClick,
                    onEditClick = onEditEntry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    editingTimestamp = uiState.editedTe?.timestamp,
                    editingContent = { _ ->
                        InlineEditorContent(
                            te = uiState.editedTe,
                            dateString = editedTeDate,
                            locationString = editedTeLocation,
                            onArrowClick = onArrowClick,
                            onNoteChange = onNoteChange
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun InlineEditorContent(
    te: TE?,
    dateString: String,
    locationString: String,
    onArrowClick: () -> Unit,
    onNoteChange: (String) -> Unit
) {
    if (te == null) return

    var noteText by remember(te.timestamp) { mutableStateOf(te.note ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = AapsSpacing.medium)
                    )
                    Text(
                        text = locationString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = onArrowClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = (te.arrow ?: TE.Arrow.NONE).directionToComposeIcon(),
                    contentDescription = stringResource(R.string.select_arrow),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        OutlinedTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                onNoteChange(it)
            },
            label = { Text(stringResource(CoreUiR.string.careportal_note)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteRotationManagementPreview() {
    MaterialTheme {
        SiteRotationManagementContent(
            uiState = SiteRotationUiState(
                isLoading = false,
                showPumpSites = true,
                showCgmSites = true
            ),
            displayEntries = listOf(
                SiteEntryDisplayData(
                    typeIcon = IcCannulaChange,
                    dateString = "10/03/2026",
                    locationString = "Front Right Upper Abdomen",
                    arrowIcon = TE.Arrow.UP.directionToComposeIcon(),
                    note = "Rotated clockwise",
                    timestamp = 1741600000000L,
                    location = TE.Location.FRONT_RIGHT_UPPER_ABDOMEN
                ),
                SiteEntryDisplayData(
                    typeIcon = IcCgmInsert,
                    dateString = "08/03/2026",
                    locationString = "Side Right Upper Arm",
                    arrowIcon = TE.Arrow.NONE.directionToComposeIcon(),
                    note = null,
                    timestamp = 1741400000000L,
                    location = TE.Location.SIDE_RIGHT_UPPER_ARM
                )
            ),
            onClose = {},
            onPreferenceClick = {},
            onShowPumpSites = {},
            onShowCgmSites = {},
            onZoneClick = {},
            onEntryClick = {},
            onEditEntry = {},
            onCancelEdit = {},
            onConfirmEdit = {},
            onArrowClick = {},
            onNoteChange = {},
            editedTeDate = "",
            editedTeLocation = ""
        )
    }
}
