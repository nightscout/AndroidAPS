package app.aaps.pump.omnipod.common.ui.compose

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodOverviewEvent
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardScreen
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import app.aaps.core.ui.R as CoreUiR

@Composable
fun OmnipodComposeHost(
    pluginName: String,
    blePreCheck: BlePreCheck,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?,
    overviewState: StateFlow<PumpOverviewUiState>,
    overviewEvents: SharedFlow<OmnipodOverviewEvent>,
    wizardViewModel: @Composable () -> OmnipodWizardViewModel,
    onConfirmDiscardPod: () -> Unit,
    activationNeedsExtraContent: () -> Boolean = { false },
    showExtraContentForHistory: Boolean = false,
    extraContent: (@Composable (onBack: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    var showWizard by remember { mutableStateOf(false) }
    var showExtraContent by remember { mutableStateOf(false) }
    var wizardActivationType by remember { mutableStateOf<ActivationType?>(null) }
    var isDeactivation by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val overviewNavIcon: @Composable () -> Unit = {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
        }
    }
    val settingsAction: @Composable RowScope.() -> Unit = {
        onSettings?.let { action ->
            IconButton(onClick = action) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(CoreUiR.string.settings))
            }
        }
    }

    LaunchedEffect(showWizard, showExtraContent) {
        if (!showWizard && !showExtraContent) {
            setToolbarConfig(ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction))
        }
    }

    LaunchedEffect(overviewEvents) {
        overviewEvents.collect { event ->
            when (event) {
                is OmnipodOverviewEvent.StartActivation         -> {
                    if (activationNeedsExtraContent()) {
                        showExtraContent = true
                    } else {
                        wizardActivationType = event.activationType
                        isDeactivation = false
                        showWizard = true
                    }
                }

                is OmnipodOverviewEvent.StartDeactivation       -> {
                    isDeactivation = true
                    showWizard = true
                }

                is OmnipodOverviewEvent.ShowHistory             -> {
                    if (showExtraContentForHistory) showExtraContent = true
                }

                is OmnipodOverviewEvent.ShowDialog              -> {
                    if (event.title == context.getString(R.string.omnipod_common_pod_management_button_discard_pod)) {
                        showDiscardConfirm = true
                    } else {
                        showDialog = true
                    }
                    dialogTitle = event.title
                    dialogMessage = event.message
                }

                is OmnipodOverviewEvent.ShowErrorDialog         -> {
                    errorTitle = event.title
                    errorMessage = event.message
                    showErrorDialog = true
                }

                is OmnipodOverviewEvent.StartActivity           -> context.startActivity(event.intent)
                is OmnipodOverviewEvent.ShowRileyLinkPairWizard -> Unit
                is OmnipodOverviewEvent.ShowRileyLinkStats      -> Unit
                is OmnipodOverviewEvent.ShowSnackbar            -> Unit
            }
        }
    }

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
                onConfirmDiscardPod()
            },
            onDismiss = { showDiscardConfirm = false }
        )
    }

    when {
        showWizard       -> {
            KeepScreenOnEffect()
            BlePreCheckHost(blePreCheck = blePreCheck, onFailed = { showWizard = false })
            val viewModel = wizardViewModel()
            val wizardReady by viewModel.ready.collectAsStateWithLifecycle()
            LaunchedEffect(wizardReady, isDeactivation, wizardActivationType) {
                if (!wizardReady) return@LaunchedEffect
                if (isDeactivation) {
                    viewModel.initializeDeactivation()
                } else {
                    wizardActivationType?.let(viewModel::initializeActivation)
                }
            }
            OmnipodWizardScreen(
                viewModel = viewModel,
                onFinish = { showWizard = false },
                setToolbarConfig = setToolbarConfig
            )
        }

        showExtraContent -> extraContent?.invoke { showExtraContent = false }
        else             -> {
            val uiState by overviewState.collectAsStateWithLifecycle()
            PumpOverviewScreen(state = uiState, customContent = { PodImage() })
        }
    }
}
