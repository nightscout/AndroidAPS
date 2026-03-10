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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.objects.extensions.directionToComposeIcon
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcSiteRotation
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
    onEditEntry: (Long) -> Unit,
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

    SiteRotationManagementContent(
        uiState = uiState,
        displayEntries = displayEntries,
        onClose = onClose,
        onPreferenceClick = onPreferenceClick,
        onShowPumpSites = { viewModel.setShowPumpSites(it) },
        onShowCgmSites = { viewModel.setShowCgmSites(it) },
        onZoneClick = { viewModel.selectLocation(it) },
        onEntryClick = { viewModel.selectLocation(it.location) },
        onEditEntry = onEditEntry,
        activity = activity
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
    activity: AppCompatActivity? = null
) {
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
                    IconButton(onClick = onPreferenceClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(CoreUiR.string.settings)
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
                        .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiChoiceSegmentedButtonRow(
                        modifier = Modifier.weight(1f)
                    ) {
                        SegmentedButton(
                            checked = uiState.showPumpSites,
                            onCheckedChange = { onShowPumpSites(!uiState.showPumpSites) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {}
                        ) {
                            Icon(
                                imageVector = IcCannulaChange,
                                contentDescription = stringResource(CoreUiR.string.careportal_pump_site_management),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        SegmentedButton(
                            checked = uiState.showCgmSites,
                            onCheckedChange = { onShowCgmSites(!uiState.showCgmSites) },
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AapsSpacing.extraLarge)
                        ) {
                            BodyView(
                                filteredLocationColor = uiState.filteredLocationColor,
                                showPumpSites = uiState.showPumpSites,
                                showCgmSites = uiState.showCgmSites,
                                selectedLocation = uiState.selectedLocation,
                                bodyType = uiState.showBodyType,
                                isFrontView = true,
                                onZoneClick = onZoneClick,
                                modifier = Modifier.weight(1f)
                            )
                            BodyView(
                                filteredLocationColor = uiState.filteredLocationColor,
                                showPumpSites = uiState.showPumpSites,
                                showCgmSites = uiState.showCgmSites,
                                selectedLocation = uiState.selectedLocation,
                                bodyType = uiState.showBodyType,
                                isFrontView = false,
                                onZoneClick = onZoneClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                SiteEntryList(
                    entries = displayEntries,
                    showEditButton = true,
                    onEntryClick = onEntryClick,
                    onEditClick = onEditEntry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
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
            onEditEntry = {}
        )
    }
}
