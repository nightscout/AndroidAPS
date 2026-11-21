package app.aaps.pump.aaruy.compose

import app.aaps.core.ui.CoreUiStrings
import app.aaps.pump.aaruy.AaruyStrings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.aaps.pump.aaruy.resources.Res
import app.aaps.pump.aaruy.resources.ic_aaruy_128
import org.jetbrains.compose.resources.painterResource
import app.aaps.core.ui.compose.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.pump.PumpOverviewScreen

private val PUMP_IMAGE_HEIGHT = 128.dp

private enum class AaruyScreen {
    OVERVIEW,
    PAIR_WIZARD,
    REPLACE_WIZARD,
    HISTORY
}

class AaruyComposeContent(
    private val pluginName: String,
    private val bleReadyCheck: AaruyBleReadyCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: AaruyOverviewViewModel = metroViewModel()
        var currentScreen by remember { mutableStateOf(AaruyScreen.OVERVIEW) }
        var showUnpairDialog by remember { mutableStateOf(false) }

        // One question: a pump the queue cannot reach is removed anyway, and a card tells the user to take it off.
        if (showUnpairDialog) OkCancelDialog(
            title = pluginName,
            icon = Icons.Filled.Bluetooth,
            message = stringResource(AaruyStrings.actions_pump_disconnect_prompt, overviewViewModel.pumpName),
            onConfirm = {
                overviewViewModel.unpair()
                showUnpairDialog = false
            },
            onDismiss = { showUnpairDialog = false }
        )

        val pairingTitle = stringResource(AaruyStrings.aaruy_pairing)
        val replaceTitle = stringResource(AaruyStrings.replace_bat_rescv_label)
        val historyTitle = stringResource(CoreUiStrings.pump_history)
        val backDescription = stringResource(CoreUiStrings.back)
        val settingsDescription = stringResource(CoreUiStrings.settings)

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
                    is AaruyOverviewEvent.ConfirmUnpair   -> showUnpairDialog = true
                }
            }
        }

        when (currentScreen) {
            AaruyScreen.OVERVIEW    -> {
                val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
                PumpOverviewScreen(
                    state = uiState,
                    customContent = {
                        Image(
                            painter = painterResource(Res.drawable.ic_aaruy_128),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PUMP_IMAGE_HEIGHT),
                            contentScale = ContentScale.Fit
                        )
                    }
                )
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
                KeepScreenOn()
                val replaceViewModel: AaruyReplaceWizardViewModel = metroViewModel()
                AaruyReplaceWizardScreen(
                    viewModel = replaceViewModel,
                    title = replaceTitle,
                    setToolbarConfig = setToolbarConfig,
                    onFinish = {
                        // It lives as long as the pump screen: the next run must start at the first step.
                        replaceViewModel.reset()
                        currentScreen = AaruyScreen.OVERVIEW
                    }
                )
            }

            AaruyScreen.PAIR_WIZARD -> {
                KeepScreenOn()
                // The scan needs the Bluetooth permissions, so show the wizard only after the check.
                var bleReady by remember { mutableStateOf(false) }
                if (!bleReady) {
                    bleReadyCheck.Check(
                        onReady = { bleReady = true },
                        onFailed = { currentScreen = AaruyScreen.OVERVIEW }
                    )
                } else {
                    val pairViewModel: AaruyPairWizardViewModel = metroViewModel()
                    AaruyPairWizardScreen(
                        viewModel = pairViewModel,
                        title = pairingTitle,
                        setToolbarConfig = setToolbarConfig,
                        onFinish = {
                            pairViewModel.reset()
                            currentScreen = AaruyScreen.OVERVIEW
                        },
                        onCancel = {
                            pairViewModel.reset()
                            currentScreen = AaruyScreen.OVERVIEW
                        }
                    )
                }
            }
        }
    }
}
