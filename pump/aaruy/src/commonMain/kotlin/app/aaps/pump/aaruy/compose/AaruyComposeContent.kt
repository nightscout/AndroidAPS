package app.aaps.pump.aaruy.compose

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
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.pump.aaruy.R
import app.aaps.core.ui.R as CoreUiR

private enum class AaruyScreen {
    OVERVIEW,
    PAIR_WIZARD,
    HISTORY,
    REPLACE_STEP
}

class AaruyComposeContent(
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
        val overviewViewModel: AaruyOverviewViewModel = metroViewModel()

        // Navigation state
        var currentScreen by remember { mutableStateOf(AaruyScreen.OVERVIEW) }

        // Dialogs
        var showUnpairDialog by remember { mutableStateOf(false) }

        if (showUnpairDialog) {
            OkCancelDialog(
                title = stringResource(CoreUiR.string.pump_unpair_confirm_title),
                icon = Icons.Filled.Bluetooth,
                message = String.format(
                    stringResource(R.string.actions_pump_disconnect_prompt),
                    overviewViewModel.pumpSn
                ),
                onConfirm = {
                    overviewViewModel.performUnpair()
                    showUnpairDialog = false
                },
                onDismiss = { showUnpairDialog = false }
            )
        }

        // Toolbar configuration
        val historyTitle = stringResource(CoreUiR.string.pump_history)
        val replaceTitle = stringResource(R.string.replace_bat_rescv_label)
        val pairingTitle = stringResource(R.string.aaruy_pairing)

        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }
        val subScreenNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { currentScreen = AaruyScreen.OVERVIEW }) {
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
                    AaruyScreen.OVERVIEW     -> ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction)
                    AaruyScreen.PAIR_WIZARD  -> ToolbarConfig(title = pairingTitle, navigationIcon = subScreenNavIcon, actions = {})
                    AaruyScreen.HISTORY      -> ToolbarConfig(title = historyTitle, navigationIcon = subScreenNavIcon, actions = {})
                    AaruyScreen.REPLACE_STEP -> ToolbarConfig(title = replaceTitle, navigationIcon = subScreenNavIcon, actions = {})
                }
            )
        }

        // Handle one-time events from overview
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is AaruyOverviewEvent.StartHistory      -> currentScreen = AaruyScreen.HISTORY
                    is AaruyOverviewEvent.StartReplaceSettings -> currentScreen = AaruyScreen.REPLACE_STEP

                    is AaruyOverviewEvent.StartPairWizard   -> {
                        currentScreen = AaruyScreen.PAIR_WIZARD
                    }

                    is AaruyOverviewEvent.ConfirmUnpair     -> showUnpairDialog = true
                }
            }
        }

        when (currentScreen) {
            AaruyScreen.OVERVIEW -> {
                AaruyOverviewScreen(
                    viewModel = overviewViewModel
                )
            }

            AaruyScreen.PAIR_WIZARD  -> {
                KeepScreenOnEffect()

                // The wizard starts a BLE scan as soon as it is shown. Without the Bluetooth
                // permissions that scan crashes the app, so show the wizard only after the check.
                var bleReady by remember { mutableStateOf(false) }
                if (!bleReady) {
                    BlePreCheckHost(
                        blePreCheck = blePreCheck,
                        onReady = { bleReady = true },
                        onFailed = { currentScreen = AaruyScreen.OVERVIEW }
                    )
                } else {
                    val pumpBindViewModel: AaruyPairWizardViewModel = metroViewModel()

                    LaunchedEffect(pumpBindViewModel) {
                        pumpBindViewModel.events.collect { event ->
                            when (event) {
                                is AaruyPairWizardEvent.PairFinish -> currentScreen = AaruyScreen.REPLACE_STEP
                            }
                        }
                    }

                    AaruyPairWizardScreen(
                        viewModel = pumpBindViewModel,
                        onFinish = { currentScreen = AaruyScreen.OVERVIEW },
                        onCancel = {
                            // pumpBindViewModel.cancel()
                            currentScreen = AaruyScreen.OVERVIEW
                        }
                    )
                }
            }

            AaruyScreen.HISTORY      -> {
                val pumpHistoryViewModel: AaruyHistoryViewModel = metroViewModel()
                AaruyHistoryScreen(viewModel = pumpHistoryViewModel)
            }

            AaruyScreen.REPLACE_STEP -> {
                val pumpReplaceViewModel: AaruyReplaceWizardViewModel = metroViewModel()
                pumpReplaceViewModel.resetData()

                LaunchedEffect(pumpReplaceViewModel) {
                    pumpReplaceViewModel.events.collect { event ->
                        when (event) {
                            is AaruyReplaceWizardEvent.RepalceFinish -> currentScreen = AaruyScreen.OVERVIEW
                        }
                    }
                }

                AaruyReplaceWizardScreen(
                    viewModel = pumpReplaceViewModel,
                    onFinish = {
                        currentScreen = AaruyScreen.OVERVIEW
                    }
                ) {
                    currentScreen = AaruyScreen.OVERVIEW
                }
            }
        }

    }

}