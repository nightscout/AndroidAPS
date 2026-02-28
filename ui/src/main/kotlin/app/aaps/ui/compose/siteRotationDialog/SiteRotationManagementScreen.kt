package app.aaps.ui.compose.siteRotationDialog

import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.ui.R
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRotationManagementScreen(
    viewModel: SiteRotationManagementViewModel,
    onClose: () -> Unit,
    onEditEntry: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val toolsColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        viewModel.resetToDefaults()
    }

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
                            imageVector = IcSiteRotation,
                            contentDescription = null,
                            tint = toolsColor,
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
                    IconButton(onClick = { /* Settings will be implemented later */ }) {
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiChoiceSegmentedButtonRow(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Bouton Pump (Cannula)
                        SegmentedButton(
                            checked = uiState.showPumpSites,
                            onCheckedChange = { viewModel.setShowPumpSites(!uiState.showPumpSites) },
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
                            onCheckedChange = { viewModel.setShowCgmSites(!uiState.showCgmSites) },
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

                Box(
                    modifier = Modifier
                        .weight(2f)
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
                            BodyView(
                                bodyType = uiState.showBodyType,
                                isFrontView = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(48f / 128f) // ou mieux : utiliser le ratio de l'image
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BodyView(
                                bodyType = uiState.showBodyType,
                                isFrontView = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(48f / 128f)
                            )
                        }
                    }
                }


                SiteEntryList(
                    entries = uiState.filteredEntries,
                    showEditButton = true,
                    dateUtil = viewModel.dateUtil,
                    translator = viewModel.translator,
                    onEntryClick = { te ->
                        viewModel.selectLocation(te.location ?: TE.Location.NONE)
                    },
                    onEditClick = { timestamp ->
                        onEditEntry(timestamp)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
