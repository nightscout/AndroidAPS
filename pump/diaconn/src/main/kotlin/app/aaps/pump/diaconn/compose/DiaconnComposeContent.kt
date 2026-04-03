package app.aaps.pump.diaconn.compose

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
import app.aaps.pump.diaconn.R

private enum class DiaconnScreen {
    OVERVIEW,
    PAIR_WIZARD,
    HISTORY,
    USER_OPTIONS
}

class DiaconnComposeContent(
    private val pluginName: String,
    private val context: Context,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: DiaconnOverviewViewModel = hiltViewModel()

        // Navigation state
        var currentScreen by remember { mutableStateOf(DiaconnScreen.OVERVIEW) }

        // Dialogs
        var showUnpairDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        errorMessage?.let { msg ->
            OkDialog(
                title = pluginName,
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
        val userOptionsTitle = stringResource(R.string.diaconng8_pump_settings)
        val pairingTitle = stringResource(R.string.diaconn_pairing)

        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }
        val subScreenNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { currentScreen = DiaconnScreen.OVERVIEW }) {
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
                    DiaconnScreen.OVERVIEW     -> ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction)
                    DiaconnScreen.PAIR_WIZARD  -> ToolbarConfig(title = pairingTitle, navigationIcon = subScreenNavIcon, actions = {})
                    DiaconnScreen.HISTORY      -> ToolbarConfig(title = historyTitle, navigationIcon = subScreenNavIcon, actions = {})
                    DiaconnScreen.USER_OPTIONS -> ToolbarConfig(title = userOptionsTitle, navigationIcon = subScreenNavIcon, actions = {})
                }
            )
        }

        // Handle one-time events from overview
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is DiaconnOverviewEvent.StartHistory      -> currentScreen = DiaconnScreen.HISTORY
                    is DiaconnOverviewEvent.StartUserSettings -> currentScreen = DiaconnScreen.USER_OPTIONS

                    is DiaconnOverviewEvent.StartPairWizard   -> {
                        currentScreen = DiaconnScreen.PAIR_WIZARD
                    }

                    is DiaconnOverviewEvent.ConfirmUnpair     -> showUnpairDialog = true
                }
            }
        }

        when (currentScreen) {
            DiaconnScreen.OVERVIEW     -> {
                DiaconnOverviewScreen(
                    viewModel = overviewViewModel
                )
            }

            DiaconnScreen.PAIR_WIZARD  -> {
                KeepScreenOnEffect()

                BlePreCheckHost(
                    blePreCheck = blePreCheck,
                    onFailed = { currentScreen = DiaconnScreen.OVERVIEW }
                )

                val wizardViewModel: DiaconnPairWizardViewModel = hiltViewModel()

                LaunchedEffect(wizardViewModel) {
                    wizardViewModel.events.collect { event ->
                        when (event) {
                            is DiaconnPairWizardEvent.Finish -> currentScreen = DiaconnScreen.OVERVIEW
                        }
                    }
                }

                DiaconnPairWizardScreen(
                    viewModel = wizardViewModel,
                    onFinish = { currentScreen = DiaconnScreen.OVERVIEW },
                    onCancel = {
                        wizardViewModel.cancel()
                        currentScreen = DiaconnScreen.OVERVIEW
                    }
                )
            }

            DiaconnScreen.HISTORY      -> {
                val historyViewModel: DiaconnHistoryViewModel = hiltViewModel()
                DiaconnHistoryScreen(viewModel = historyViewModel)
            }

            DiaconnScreen.USER_OPTIONS -> {
                val userOptionsViewModel: DiaconnUserOptionsViewModel = hiltViewModel()

                LaunchedEffect(userOptionsViewModel) {
                    userOptionsViewModel.events.collect { event ->
                        when (event) {
                            is DiaconnUserOptionsEvent.Saved -> currentScreen = DiaconnScreen.OVERVIEW
                            is DiaconnUserOptionsEvent.Error -> errorMessage = event.message
                        }
                    }
                }

                DiaconnUserOptionsScreen(
                    viewModel = userOptionsViewModel
                )
            }
        }
    }
}
