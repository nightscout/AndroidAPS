package app.aaps.pump.medtronic.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.pump.common.compose.RileyLinkPairWizardEvent
import app.aaps.pump.common.compose.RileyLinkPairWizardScreen
import app.aaps.pump.common.compose.RileyLinkPairWizardViewModel
import app.aaps.pump.common.compose.RileyLinkStatusScreen
import app.aaps.pump.common.compose.RileyLinkStatusViewModel
import app.aaps.pump.medtronic.R

class MedtronicComposeContent(
    private val pluginName: String,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: MedtronicOverviewViewModel = hiltViewModel()
        val snackbarHostState = LocalSnackbarHostState.current

        // Navigation state
        var showRileyLinkPairWizard by remember { mutableStateOf(false) }
        var showHistory by remember { mutableStateOf(false) }
        var showRileyLinkStats by remember { mutableStateOf(false) }

        // Dialog state
        var showDialog by remember { mutableStateOf(false) }
        var dialogTitle by remember { mutableStateOf("") }
        var dialogMessage by remember { mutableStateOf("") }

        // Toolbar
        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(app.aaps.core.ui.R.string.settings))
                }
            }
        }

        val historyTitle = stringResource(app.aaps.core.ui.R.string.pump_history)
        val subScreenNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { showHistory = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }

        val rlStatsTitle = stringResource(app.aaps.core.ui.R.string.settings)
        val rlStatsNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { showRileyLinkStats = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }

        LaunchedEffect(showRileyLinkPairWizard, showHistory, showRileyLinkStats) {
            when {
                showHistory              -> setToolbarConfig(ToolbarConfig(title = historyTitle, navigationIcon = subScreenNavIcon, actions = {}))
                showRileyLinkStats       -> setToolbarConfig(ToolbarConfig(title = rlStatsTitle, navigationIcon = rlStatsNavIcon, actions = {}))
                !showRileyLinkPairWizard -> setToolbarConfig(ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction))
            }
        }

        // Handle events
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is MedtronicOverviewEvent.ShowHistory             -> {
                        showHistory = true
                    }

                    is MedtronicOverviewEvent.ShowRileyLinkPairWizard -> {
                        showRileyLinkPairWizard = true
                    }

                    is MedtronicOverviewEvent.ShowRileyLinkStats      -> {
                        showRileyLinkStats = true
                    }

                    is MedtronicOverviewEvent.ShowDialog              -> {
                        dialogTitle = event.title
                        dialogMessage = event.message
                        showDialog = true
                    }

                    is MedtronicOverviewEvent.ShowSnackbar            -> {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }

        // Dialog
        if (showDialog) {
            OkDialog(title = dialogTitle, message = dialogMessage, onDismiss = { showDialog = false })
        }

        // Content
        when {
            showRileyLinkPairWizard -> {
                var bleCheckPassed by remember { mutableStateOf(false) }

                if (!bleCheckPassed) {
                    BlePreCheckHost(
                        blePreCheck = blePreCheck,
                        onReady = { bleCheckPassed = true },
                        onFailed = { showRileyLinkPairWizard = false }
                    )
                } else {
                    val rlWizardViewModel: RileyLinkPairWizardViewModel = hiltViewModel()

                    LaunchedEffect(rlWizardViewModel) {
                        rlWizardViewModel.events.collect { event ->
                            when (event) {
                                is RileyLinkPairWizardEvent.Finish ->
                                    showRileyLinkPairWizard = false
                            }
                        }
                    }

                    RileyLinkPairWizardScreen(
                        viewModel = rlWizardViewModel,
                        onFinish = { showRileyLinkPairWizard = false },
                        onCancel = { showRileyLinkPairWizard = false }
                    )
                }
            }

            showRileyLinkStats      -> {
                val rlStatusViewModel: RileyLinkStatusViewModel = hiltViewModel()
                RileyLinkStatusScreen(viewModel = rlStatusViewModel)
            }

            showHistory             -> {
                val historyViewModel: MedtronicHistoryViewModel = hiltViewModel()
                val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()

                MedtronicHistoryScreen(
                    state = historyState,
                    groups = historyViewModel.groups,
                    rh = historyViewModel.rh,
                    onSelectGroup = historyViewModel::selectGroup
                )
            }

            else                    -> {
                val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
                PumpOverviewScreen(
                    state = uiState,
                    customContent = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.ic_medtronic_veo),
                                contentDescription = null,
                                modifier = Modifier.height(100.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                )
            }
        }
    }
}
