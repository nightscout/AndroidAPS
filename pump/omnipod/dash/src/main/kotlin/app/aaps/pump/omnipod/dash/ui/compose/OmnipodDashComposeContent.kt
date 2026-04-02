package app.aaps.pump.omnipod.dash.ui.compose

import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.pump.omnipod.common.ui.compose.PodImage
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodOverviewEvent
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardScreen
import app.aaps.pump.omnipod.dash.ui.wizard.compose.DashOmnipodWizardViewModel

class OmnipodDashComposeContent(
    private val pluginName: String,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: DashOverviewViewModel = hiltViewModel()
        val context = LocalContext.current

        // Workflow state
        var showWizard by remember { mutableStateOf(false) }
        var showHistory by remember { mutableStateOf(false) }
        var wizardActivationType by remember { mutableStateOf<ActivationType?>(null) }
        var isDeactivation by remember { mutableStateOf(false) }

        // Dialog state
        var showDialog by remember { mutableStateOf(false) }
        var dialogTitle by remember { mutableStateOf("") }
        var dialogMessage by remember { mutableStateOf("") }
        var showDiscardConfirm by remember { mutableStateOf(false) }
        var showErrorDialog by remember { mutableStateOf(false) }
        var errorTitle by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }

        // Overview toolbar
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

        // Restore overview toolbar when not in wizard
        LaunchedEffect(showWizard) {
            if (!showWizard) {
                setToolbarConfig(ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction))
            }
        }

        // Handle overview events
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is OmnipodOverviewEvent.StartActivation         -> {
                        wizardActivationType = event.activationType
                        isDeactivation = false
                        showWizard = true
                    }

                    is OmnipodOverviewEvent.StartDeactivation       -> {
                        isDeactivation = true
                        showWizard = true
                    }

                    is OmnipodOverviewEvent.ShowHistory             -> {
                        showHistory = true
                    }

                    is OmnipodOverviewEvent.ShowDialog              -> {
                        // Check if this is a discard confirmation
                        if (event.title == context.getString(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_discard_pod)) {
                            showDiscardConfirm = true
                            dialogTitle = event.title
                            dialogMessage = event.message
                        } else {
                            dialogTitle = event.title
                            dialogMessage = event.message
                            showDialog = true
                        }
                    }

                    is OmnipodOverviewEvent.ShowErrorDialog         -> {
                        errorTitle = event.title
                        errorMessage = event.message
                        showErrorDialog = true
                    }

                    is OmnipodOverviewEvent.StartActivity           -> {
                        context.startActivity(event.intent)
                    }

                    is OmnipodOverviewEvent.ShowRileyLinkPairWizard -> Unit // Not applicable for Dash
                    is OmnipodOverviewEvent.ShowRileyLinkStats      -> Unit // Not applicable for Dash
                    is OmnipodOverviewEvent.ShowSnackbar            -> Unit // Not used by Dash
                }
            }
        }

        // Dialogs
        if (showDialog) {
            OkDialog(title = dialogTitle, message = dialogMessage, onDismiss = { showDialog = false })
        }

        if (showErrorDialog) {
            OkDialog(title = errorTitle, message = errorMessage, onDismiss = { showErrorDialog = false })
        }

        if (showDiscardConfirm) {
            OkCancelDialog(
                title = dialogTitle,
                message = dialogMessage,
                onConfirm = {
                    showDiscardConfirm = false
                    overviewViewModel.confirmDiscardPod()
                },
                onDismiss = { showDiscardConfirm = false }
            )
        }

        // Content: overview, wizard, or history
        when {
            showWizard  -> {
                KeepScreenOnEffect()
                BlePreCheckHost(
                    blePreCheck = blePreCheck,
                    onFailed = { showWizard = false }
                )

                val wizardViewModel: DashOmnipodWizardViewModel = hiltViewModel()
                val wizardReady by wizardViewModel.ready.collectAsStateWithLifecycle()
                LaunchedEffect(wizardReady, isDeactivation, wizardActivationType) {
                    if (!wizardReady) return@LaunchedEffect
                    if (isDeactivation) {
                        wizardViewModel.initializeDeactivation()
                    } else {
                        wizardActivationType?.let { wizardViewModel.initializeActivation(it) }
                    }
                }

                OmnipodWizardScreen(
                    viewModel = wizardViewModel,
                    onFinish = { showWizard = false },
                    setToolbarConfig = setToolbarConfig
                )
            }

            showHistory -> {
                val historyViewModel: DashPodHistoryViewModel = hiltViewModel()
                val records by historyViewModel.records.collectAsStateWithLifecycle()

                // Set toolbar with back arrow for history
                LaunchedEffect(Unit) {
                    setToolbarConfig(
                        ToolbarConfig(
                            title = context.getString(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_pod_history),
                            navigationIcon = {
                                IconButton(onClick = { showHistory = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                                }
                            },
                            actions = {}
                        ))
                }

                DashPodHistoryScreen(
                    records = records,
                    rh = historyViewModel.rh,
                    profileUtil = historyViewModel.profileUtil
                )
            }

            else        -> {
                val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
                PumpOverviewScreen(
                    state = uiState,
                    customContent = { PodImage() }
                )
            }
        }
    }
}
