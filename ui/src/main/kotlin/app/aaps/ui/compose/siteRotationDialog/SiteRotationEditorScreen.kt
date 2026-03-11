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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.R
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationEditorViewModel
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationUiState
import kotlinx.coroutines.launch
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRotationEditorScreen(
    viewModel: SiteRotationEditorViewModel,
    timestamp: Long,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showArrowDialog by remember { mutableStateOf(false) }
    if (showArrowDialog) {
        ArrowSelectionDialog(
            onDismiss = { showArrowDialog = false },
            onArrowSelected = { arrow ->
                viewModel.updateArrow(arrow)
                showArrowDialog = false
            }
        )
    }

    // Confirmation dialog
    if (showConfirmation) {
        if (!uiState.isEdited || uiState.editedTe == null) {
            showConfirmation = false
        } else {
            val summaryLines = viewModel.buildConfirmationSummary()
            OkCancelDialog(
                title = stringResource(R.string.record_site_change),
                message = summaryLines.joinToString("<br/>"),
                icon = when (uiState.editedTe?.type) {
                    TE.Type.CANNULA_CHANGE -> IcCannulaChange
                    TE.Type.SENSOR_CHANGE  -> IcCgmInsert
                    else                   -> IcSiteRotation      // editedTE null, should never occur
                },
                iconTint = AapsTheme.elementColors.tempBasal,
                onConfirm = {
                    viewModel.confirmAndSave()
                    onClose()
                },
                onDismiss = { showConfirmation = false }
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetToDefaults()
        viewModel.loadEntryByTimestamp(timestamp)
    }

    SiteRotationEditorContent(
        uiState = uiState,
        onClose = onClose,
        viewModel = viewModel,
        onConfirmClick = { showConfirmation = true },
        onArrowClick = { showArrowDialog = true },
        activity = activity
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRotationEditorContent(
    uiState: SiteRotationUiState,
    viewModel: SiteRotationEditorViewModel,
    onClose: () -> Unit,
    onConfirmClick: () -> Unit,
    onArrowClick: () -> Unit,
    activity: AppCompatActivity?
) {

    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (uiState.editedTe?.type) {
                                TE.Type.CANNULA_CHANGE -> IcCannulaChange
                                TE.Type.SENSOR_CHANGE  -> IcCgmInsert
                                else                   -> IcSiteRotation      // editedTE null, should never occur
                            },
                            contentDescription = null,
                            tint = AapsTheme.elementColors.tempBasal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(CoreUiR.string.site_rotation))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onConfirmClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(CoreUiR.string.save),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiteRotationEditorDetails(
                        te = uiState.editedTe,
                        onArrowClick = onArrowClick,
                        onNoteChange = { viewModel.updateNote(it) },
                        dateUtil = viewModel.dateUtil,
                        translator = viewModel.translator
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiChoiceSegmentedButtonRow(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Bouton Pump (Cannula)
                        SegmentedButton(
                            checked = uiState.showPumpSites,
                            onCheckedChange = { viewModel.setShowPumpSites(!uiState.showPumpSites || uiState.editedTe?.type == TE.Type.CANNULA_CHANGE) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {}
                        ) {
                            Icon(
                                imageVector = IcCannulaChange,
                                contentDescription = stringResource(CoreUiR.string.careportal_pump_site_management),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // Bouton CGM (Sensor)
                        SegmentedButton(
                            checked = uiState.showCgmSites,
                            onCheckedChange = { viewModel.setShowCgmSites(!uiState.showCgmSites || uiState.editedTe?.type == TE.Type.SENSOR_CHANGE) },
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

                    // Info tooltip (optionnel)
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

                var isFrontView by remember { mutableStateOf(true) }
                SecondaryTabRow(
                    selectedTabIndex = if (isFrontView) 0 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    tabs = {
                        Tab(
                            selected = isFrontView,
                            onClick = { isFrontView = true },
                            text = { Text(stringResource(R.string.site_front)) }
                        )
                        Tab(
                            selected = !isFrontView,
                            onClick = { isFrontView = false },
                            text = { Text(stringResource(R.string.site_back)) }
                        )
                    }
                )
                Box(
                    modifier = Modifier
                        .weight(2.2f * uiState.showBodyType.sizeRatio)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.width(32.dp))
                            BodyView(
                                filteredLocationColor = uiState.filteredLocationColor,
                                showPumpSites = uiState.showPumpSites,
                                showCgmSites = uiState.showCgmSites,
                                selectedLocation = uiState.selectedLocation,
                                bodyType = uiState.showBodyType,
                                isFrontView = isFrontView,
                                onZoneClick = { location ->
                                    viewModel.selectLocation(location)
                                    viewModel.updateLocation(location)
                                },
                                modifier = Modifier
                                    .weight(1f),
                                editedTe = uiState.editedTe
                            )
                            Spacer(modifier = Modifier.width(32.dp))
                        }
                    }
                }


                SiteEntryList(
                    filteredEntries = uiState.filteredEntries,
                    showEditButton = false,
                    dateUtil = viewModel.dateUtil,
                    translator = viewModel.translator,
                    onEntryClick = { te ->
                        viewModel.selectLocation(te.location ?: TE.Location.NONE)
                    },
                    onEditClick = { timestamp ->
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}