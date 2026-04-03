package app.aaps.pump.danar.compose

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
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.compose.DanaHistoryScreen
import app.aaps.pump.dana.compose.DanaHistoryViewModel
import app.aaps.pump.dana.compose.DanaOverviewEvent
import app.aaps.pump.dana.compose.DanaOverviewScreen
import app.aaps.pump.dana.compose.DanaOverviewViewModel
import app.aaps.pump.dana.compose.DanaUserOptionsScreen
import app.aaps.pump.dana.compose.DanaUserOptionsViewModel
import app.aaps.pump.dana.compose.UserOptionsEvent

private enum class DanaRScreen {
    OVERVIEW,
    PAIR_WIZARD,
    HISTORY,
    USER_OPTIONS
}

class DanaRComposeContent(
    private val pluginName: String,
    private val danaPump: DanaPump
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: DanaOverviewViewModel = hiltViewModel()
        val wizardViewModel: DanaRPairWizardViewModel = hiltViewModel()

        // Navigation state
        var currentScreen by remember { mutableStateOf(DanaRScreen.OVERVIEW) }

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
            IconButton(onClick = { currentScreen = DanaRScreen.OVERVIEW }) {
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
                    DanaRScreen.OVERVIEW     -> ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction)
                    DanaRScreen.PAIR_WIZARD  -> ToolbarConfig(title = pairingTitle, navigationIcon = subScreenNavIcon, actions = {})
                    DanaRScreen.HISTORY      -> ToolbarConfig(title = historyTitle, navigationIcon = subScreenNavIcon, actions = {})
                    DanaRScreen.USER_OPTIONS -> ToolbarConfig(title = userOptionsTitle, navigationIcon = subScreenNavIcon, actions = {})
                }
            )
        }

        // Handle one-time events from overview
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is DanaOverviewEvent.StartHistory      -> currentScreen = DanaRScreen.HISTORY
                    is DanaOverviewEvent.StartUserSettings -> currentScreen = DanaRScreen.USER_OPTIONS

                    is DanaOverviewEvent.StartPairWizard   -> {
                        wizardViewModel.reset()
                        currentScreen = DanaRScreen.PAIR_WIZARD
                    }

                    is DanaOverviewEvent.ConfirmUnpair     -> showUnpairDialog = true
                }
            }
        }

        when (currentScreen) {
            DanaRScreen.OVERVIEW     -> {
                DanaOverviewScreen(
                    viewModel = overviewViewModel,
                    danaPump = danaPump
                )
            }

            DanaRScreen.PAIR_WIZARD  -> {
                LaunchedEffect(wizardViewModel) {
                    wizardViewModel.events.collect { event ->
                        when (event) {
                            is DanaRPairWizardEvent.Finish -> currentScreen = DanaRScreen.OVERVIEW
                        }
                    }
                }

                DanaRPairWizardScreen(
                    viewModel = wizardViewModel,
                    onCancel = { currentScreen = DanaRScreen.OVERVIEW }
                )
            }

            DanaRScreen.HISTORY      -> {
                val historyViewModel: DanaHistoryViewModel = hiltViewModel()
                DanaHistoryScreen(viewModel = historyViewModel)
            }

            DanaRScreen.USER_OPTIONS -> {
                val userOptionsViewModel: DanaUserOptionsViewModel = hiltViewModel()

                LaunchedEffect(userOptionsViewModel) {
                    userOptionsViewModel.events.collect { event ->
                        when (event) {
                            is UserOptionsEvent.Saved -> currentScreen = DanaRScreen.OVERVIEW
                            is UserOptionsEvent.Error -> errorMessage = event.message
                        }
                    }
                }

                DanaUserOptionsScreen(viewModel = userOptionsViewModel)
            }
        }
    }
}
