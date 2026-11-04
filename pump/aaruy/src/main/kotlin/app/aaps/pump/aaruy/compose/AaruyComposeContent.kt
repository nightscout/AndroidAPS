package app.aaps.pump.aaruy.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.pump.aaruy.R

private enum class AaruyScreen {
    OVERVIEW,
    PAIR_WIZARD,
    REPLACE_WIZARD,
    HISTORY
}

/** How far the unpair question has come. A pump out of reach is asked about twice, as it stays paired on its side. */
private enum class UnpairDialog {
    NONE,
    FIRST,
    SECOND
}

class AaruyComposeContent(
    private val pluginName: String,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: AaruyOverviewViewModel = metroViewModel()
        var currentScreen by remember { mutableStateOf(AaruyScreen.OVERVIEW) }
        var unpairDialog by remember { mutableStateOf(UnpairDialog.NONE) }

        when (unpairDialog) {
            UnpairDialog.NONE   -> Unit
            UnpairDialog.FIRST  -> {
                val connected = overviewViewModel.isConnected
                OkCancelDialog(
                    title = pluginName,
                    icon = Icons.Filled.Bluetooth,
                    message = stringResource(if (connected) R.string.actions_pump_disconnect_prompt else R.string.actions_pump_disconnect, overviewViewModel.pumpName),
                    onConfirm = {
                        if (connected) {
                            overviewViewModel.performUnpair()
                            unpairDialog = UnpairDialog.NONE
                        } else {
                            unpairDialog = UnpairDialog.SECOND
                        }
                    },
                    onDismiss = { unpairDialog = UnpairDialog.NONE }
                )
            }

            UnpairDialog.SECOND -> OkCancelDialog(
                title = pluginName,
                icon = Icons.Filled.Bluetooth,
                message = stringResource(R.string.actions_pump_disconnect_again, overviewViewModel.pumpName),
                onConfirm = {
                    overviewViewModel.performUnpair()
                    unpairDialog = UnpairDialog.NONE
                },
                onDismiss = { unpairDialog = UnpairDialog.NONE }
            )
        }

        val pairingTitle = stringResource(R.string.aaruy_pairing)
        val replaceTitle = stringResource(R.string.replace_bat_rescv_label)
        val historyTitle = stringResource(app.aaps.core.ui.R.string.pump_history)
        val backDescription = stringResource(app.aaps.core.ui.R.string.back)
        val settingsDescription = stringResource(app.aaps.core.ui.R.string.settings)

        // A wizard sets the toolbar itself, so that its back arrow asks before it leaves.
        LaunchedEffect(currentScreen) {
            if (currentScreen == AaruyScreen.HISTORY) {
                setToolbarConfig(
                    ToolbarConfig(
                        title = historyTitle,
                        navigationIcon = {
                            IconButton(onClick = { currentScreen = AaruyScreen.OVERVIEW }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription) }
                        },
                        actions = {}
                    )
                )
            }
            if (currentScreen == AaruyScreen.OVERVIEW) {
                setToolbarConfig(
                    ToolbarConfig(
                        title = pluginName,
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription) }
                        },
                        actions = {
                            onSettings?.let { action ->
                                IconButton(onClick = action) { Icon(Icons.Filled.Settings, contentDescription = settingsDescription) }
                            }
                        }
                    )
                )
            }
        }

        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is AaruyOverviewEvent.StartPairWizard -> currentScreen = AaruyScreen.PAIR_WIZARD
                    is AaruyOverviewEvent.StartReplace    -> currentScreen = AaruyScreen.REPLACE_WIZARD
                    is AaruyOverviewEvent.StartHistory    -> currentScreen = AaruyScreen.HISTORY
                    is AaruyOverviewEvent.ConfirmUnpair   -> unpairDialog = UnpairDialog.FIRST
                }
            }
        }

        when (currentScreen) {
            AaruyScreen.OVERVIEW    -> {
                val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
                PumpOverviewScreen(state = uiState)
            }

            AaruyScreen.HISTORY -> {
                // The system back button returns to the pump screen too, as the arrow in the toolbar does.
                NavigationBackHandler(
                    state = rememberNavigationEventState(NavigationEventInfo.None),
                    isBackEnabled = true,
                    onBackCompleted = { currentScreen = AaruyScreen.OVERVIEW }
                )
                AaruyHistoryScreen(viewModel = metroViewModel())
            }

            AaruyScreen.REPLACE_WIZARD -> {
                KeepScreenOnEffect()
                AaruyReplaceWizardScreen(
                    viewModel = metroViewModel(),
                    title = replaceTitle,
                    setToolbarConfig = setToolbarConfig,
                    onFinish = { currentScreen = AaruyScreen.OVERVIEW }
                )
            }

            AaruyScreen.PAIR_WIZARD -> {
                KeepScreenOnEffect()
                // The scan needs the Bluetooth permissions, so show the wizard only after the check.
                var bleReady by remember { mutableStateOf(false) }
                if (!bleReady) {
                    BlePreCheckHost(
                        blePreCheck = blePreCheck,
                        onReady = { bleReady = true },
                        onFailed = { currentScreen = AaruyScreen.OVERVIEW }
                    )
                } else {
                    AaruyPairWizardScreen(
                        viewModel = metroViewModel(),
                        title = pairingTitle,
                        setToolbarConfig = setToolbarConfig,
                        onFinish = { currentScreen = AaruyScreen.OVERVIEW },
                        onCancel = { currentScreen = AaruyScreen.OVERVIEW }
                    )
                }
            }
        }
    }
}
