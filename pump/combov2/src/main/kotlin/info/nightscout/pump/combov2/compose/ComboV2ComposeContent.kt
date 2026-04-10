package info.nightscout.pump.combov2.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
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
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.combov2.R

private enum class ComboScreen { OVERVIEW, PAIR_WIZARD }

class ComboV2ComposeContent(
    private val pluginName: String,
    private val combov2Plugin: ComboV2Plugin
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: ComboV2OverviewViewModel = hiltViewModel()
        val wizardViewModel: ComboV2PairWizardViewModel = hiltViewModel()

        var currentScreen by remember { mutableStateOf(ComboScreen.OVERVIEW) }
        var showUnpairDialog by remember { mutableStateOf(false) }

        if (showUnpairDialog) {
            OkCancelDialog(
                title = stringResource(R.string.combov2_unpair_pump_title),
                icon = Icons.Filled.BluetoothDisabled,
                message = stringResource(R.string.combov2_confirm_unpair_message),
                secondMessage = stringResource(R.string.combov2_unpair_pump_summary),
                onConfirm = {
                    overviewViewModel.performUnpair()
                    showUnpairDialog = false
                },
                onDismiss = { showUnpairDialog = false }
            )
        }

        val pairingTitle = stringResource(R.string.combov2_pair_with_pump_title)

        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                )
            }
        }
        val leaveWizard: () -> Unit = {
            wizardViewModel.cancelIfInProgress()
            currentScreen = ComboScreen.OVERVIEW
        }
        val subScreenNavIcon: @Composable () -> Unit = {
            IconButton(onClick = leaveWizard) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                )
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.settings)
                    )
                }
            }
        }

        LaunchedEffect(currentScreen) {
            setToolbarConfig(
                when (currentScreen) {
                    ComboScreen.OVERVIEW    -> ToolbarConfig(
                        title = pluginName,
                        navigationIcon = overviewNavIcon,
                        actions = settingsAction
                    )

                    ComboScreen.PAIR_WIZARD -> ToolbarConfig(
                        title = pairingTitle,
                        navigationIcon = subScreenNavIcon,
                        actions = {}
                    )
                }
            )
        }

        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    ComboV2OverviewEvent.StartPairWizard -> {
                        wizardViewModel.reset()
                        currentScreen = ComboScreen.PAIR_WIZARD
                    }

                    ComboV2OverviewEvent.ConfirmUnpair   -> showUnpairDialog = true
                }
            }
        }

        when (currentScreen) {
            ComboScreen.OVERVIEW    -> ComboV2OverviewScreen(viewModel = overviewViewModel)

            ComboScreen.PAIR_WIZARD -> ComboV2PairWizardScreen(
                viewModel = wizardViewModel,
                combov2Plugin = combov2Plugin,
                onFinish = leaveWizard
            )
        }
    }
}
