package app.aaps.pump.danars.compose

import android.content.Context
import androidx.compose.foundation.layout.RowScope
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
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.compose.DanaHistoryScreen
import app.aaps.pump.dana.compose.DanaHistoryViewModel
import app.aaps.pump.dana.compose.DanaOverviewEvent
import app.aaps.pump.dana.compose.DanaUserOptionsScreen
import app.aaps.pump.dana.compose.DanaUserOptionsViewModel
import app.aaps.pump.dana.compose.UserOptionsEvent

private enum class DanaScreen {
    OVERVIEW,
    PAIR_WIZARD,
    HISTORY,
    USER_OPTIONS
}

class DanaRSComposeContent(
    private val pluginName: String,
    private val context: Context,
    private val danaPump: DanaPump,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: DanaRSOverviewViewModel = hiltViewModel()
        val wizardViewModel: DanaRSPairWizardViewModel = hiltViewModel()

        // Navigation state
        var currentScreen by remember { mutableStateOf(DanaScreen.OVERVIEW) }

        // Dialogs
        var showUnpairDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        errorMessage?.let { msg ->
            OkDialog(
                title = stringResource(R.string.pumperror),
                message = msg,
                onDismiss = { errorMessage = null }
            )
        }

        if (showUnpairDialog) {
            OkCancelDialog(
                title = pluginName,
                icon = Icons.Filled.Bluetooth,
                message = stringResource(R.string.resetpairing),
                onConfirm = {
                    overviewViewModel.performUnpair()
                    showUnpairDialog = false
                },
                onDismiss = { showUnpairDialog = false }
            )
        }

        // Toolbar configuration
        val historyTitle = stringResource(app.aaps.core.ui.R.string.pump_history)
        val userOptionsTitle = stringResource(R.string.danar_pump_settings)
        val pairingTitle = stringResource(app.aaps.core.ui.R.string.pairing)

        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }
        val subScreenNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { currentScreen = DanaScreen.OVERVIEW }) {
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

        LaunchedEffect(currentScreen) {
            setToolbarConfig(
                when (currentScreen) {
                    DanaScreen.OVERVIEW     -> ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction)
                    DanaScreen.PAIR_WIZARD  -> ToolbarConfig(title = pairingTitle, navigationIcon = subScreenNavIcon, actions = {})
                    DanaScreen.HISTORY      -> ToolbarConfig(title = historyTitle, navigationIcon = subScreenNavIcon, actions = {})
                    DanaScreen.USER_OPTIONS -> ToolbarConfig(title = userOptionsTitle, navigationIcon = subScreenNavIcon, actions = {})
                }
            )
        }

        // Handle one-time events from overview
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is DanaOverviewEvent.StartHistory      -> currentScreen = DanaScreen.HISTORY
                    is DanaOverviewEvent.StartUserSettings -> currentScreen = DanaScreen.USER_OPTIONS

                    is DanaOverviewEvent.StartPairWizard   -> {
                        wizardViewModel.reset()
                        currentScreen = DanaScreen.PAIR_WIZARD
                    }

                    is DanaOverviewEvent.ConfirmUnpair     -> showUnpairDialog = true
                }
            }
        }

        when (currentScreen) {
            DanaScreen.OVERVIEW     -> {
                DanaRSOverviewScreen(
                    viewModel = overviewViewModel,
                    danaPump = danaPump
                )
            }

            DanaScreen.PAIR_WIZARD  -> {
                KeepScreenOnEffect()

                if (!wizardViewModel.isEmulating) {
                    BlePreCheckHost(
                        blePreCheck = blePreCheck,
                        onFailed = { currentScreen = DanaScreen.OVERVIEW }
                    )
                }

                LaunchedEffect(wizardViewModel) {
                    wizardViewModel.events.collect { event ->
                        when (event) {
                            is PairWizardEvent.Finish -> currentScreen = DanaScreen.OVERVIEW
                        }
                    }
                }

                DanaRSPairWizardScreen(
                    viewModel = wizardViewModel,
                    onFinish = { currentScreen = DanaScreen.OVERVIEW },
                    onCancel = {
                        wizardViewModel.cancel()
                        currentScreen = DanaScreen.OVERVIEW
                    }
                )
            }

            DanaScreen.HISTORY      -> {
                val historyViewModel: DanaHistoryViewModel = hiltViewModel()
                DanaHistoryScreen(viewModel = historyViewModel)
            }

            DanaScreen.USER_OPTIONS -> {
                val userOptionsViewModel: DanaUserOptionsViewModel = hiltViewModel()

                LaunchedEffect(userOptionsViewModel) {
                    userOptionsViewModel.events.collect { event ->
                        when (event) {
                            is UserOptionsEvent.Saved -> currentScreen = DanaScreen.OVERVIEW
                            is UserOptionsEvent.Error -> errorMessage = event.message
                        }
                    }
                }

                DanaUserOptionsScreen(
                    viewModel = userOptionsViewModel
                )
            }
        }
    }
}
