package app.aaps.pump.ap550s.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.pump.BluetoothPermissionsHost
import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550s.compose.pairing.Ap550PairWizardScreen
import app.aaps.pump.ap550s.compose.pairing.Ap550PairWizardViewModel
import app.aaps.pump.ap550s.compose.qr.Ap550QrScanScreen
import kotlinx.coroutines.delay

/** The screens the AP550 driver shows under its own toolbar. */
private enum class Ap550Screen {
    OVERVIEW,
    PAIR_WIZARD,
    HISTORY,
    CONNECT_STATUS,
    USER_OPTIONS,
    QR_SCAN
}

/**
 * How long the reconnect notice stays on screen.
 *
 * The reminder is repeated on a timer while a pump does not answer, so it has to go away on its own.
 * It is a bit shorter than the repeat, so the user is never shown two dialogs of the same kind.
 */
private const val RECONNECT_DIALOG_MS = 119_000L

/**
 * Compose host for the AP550 driver.
 *
 * The overview is the root. The pairing wizard, the history, the connect status list and the user
 * options are one level below it. The QR screen is opened from the wizard and hands back the serial
 * number it read, so the wizard can pair with that pump.
 */
class Ap550ComposeContent(
    private val pluginName: String
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: Ap550OverviewViewModel = metroViewModel()
        val wizardViewModel: Ap550PairWizardViewModel = metroViewModel()
        val historyViewModel: Ap550HistoryViewModel = metroViewModel()
        val connectStatusViewModel: Ap550ConnectStatusViewModel = metroViewModel()
        val userOptionsViewModel: Ap550UserOptionsViewModel = metroViewModel()

        var currentScreen by remember { mutableStateOf(Ap550Screen.OVERVIEW) }
        var showReconnectDialog by remember { mutableStateOf(false) }
        var confirmSuspendResume by remember { mutableStateOf(false) }
        var confirmMute by remember { mutableStateOf(false) }

        // The stop/resume and mute buttons live in the overview's bottom button area. Both change
        // what the pump does, so both ask first.
        val connection by overviewViewModel.connectionState.collectAsStateWithLifecycle()

        if (confirmSuspendResume) {
            val suspend = !connection.pumpSuspended
            OkCancelDialog(
                title = stringResource(R.string.ap550_dialog_notice_title),
                message = stringResource(if (suspend) R.string.ap550_confirm_suspend_pump else R.string.ap550_confirm_resume_pump),
                onConfirm = {
                    confirmSuspendResume = false
                    overviewViewModel.suspendResumePump(suspend)
                },
                onDismiss = { confirmSuspendResume = false }
            )
        }

        if (confirmMute) {
            // A muted pump is asked to turn its sound back on, and the other way round.
            val turnSoundOn = connection.pumpMuted
            OkCancelDialog(
                title = stringResource(R.string.ap550_dialog_notice_title),
                message = stringResource(if (turnSoundOn) R.string.ap550_confirm_pump_sound_on else R.string.ap550_confirm_pump_sound_off),
                onConfirm = {
                    confirmMute = false
                    overviewViewModel.mutePump()
                },
                onDismiss = { confirmMute = false }
            )
        }

        if (showReconnectDialog) {
            // The notice is a reminder, not a question, so it closes itself after a while.
            LaunchedEffect(Unit) {
                delay(RECONNECT_DIALOG_MS)
                showReconnectDialog = false
            }
            OkDialog(
                title = stringResource(R.string.ap550s_connect_fail),
                message = stringResource(R.string.pump_connect_fail_content),
                onDismiss = { showReconnectDialog = false }
            )
        }

        // Read here: stringResource is @Composable, so it cannot be called from the LaunchedEffect
        // that hands the toolbar config up.
        val pairWizardTitle = stringResource(R.string.ap550_pair_with_pump_title)
        val qrScanTitle = stringResource(R.string.ap550_qr_scan_title)
        val historyTitle = stringResource(CoreUiR.string.pump_history)
        val connectStatusTitle = stringResource(R.string.connect_status)
        val userOptionsTitle = stringResource(R.string.ap550s_user_options)

        val closeWizard: () -> Unit = { currentScreen = Ap550Screen.OVERVIEW }
        val backToOverview: @Composable () -> Unit = {
            IconButton(onClick = { currentScreen = Ap550Screen.OVERVIEW }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreUiR.string.back)
                )
            }
        }
        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreUiR.string.back)
                )
            }
        }
        // The wizard cleans up what an unfinished attempt had started, so the host asks it to leave
        // instead of only changing the screen.
        val wizardNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { wizardViewModel.leaveWizard() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreUiR.string.back)
                )
            }
        }
        val qrNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { currentScreen = Ap550Screen.PAIR_WIZARD }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreUiR.string.back)
                )
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(CoreUiR.string.settings)
                    )
                }
            }
        }

        LaunchedEffect(currentScreen) {
            setToolbarConfig(
                when (currentScreen) {
                    Ap550Screen.OVERVIEW       -> ToolbarConfig(
                        title = pluginName,
                        navigationIcon = overviewNavIcon,
                        actions = settingsAction
                    )

                    Ap550Screen.PAIR_WIZARD    -> ToolbarConfig(
                        title = pairWizardTitle,
                        navigationIcon = wizardNavIcon,
                        actions = {}
                    )

                    Ap550Screen.QR_SCAN        -> ToolbarConfig(
                        title = qrScanTitle,
                        navigationIcon = qrNavIcon,
                        actions = {}
                    )

                    Ap550Screen.HISTORY        -> ToolbarConfig(
                        title = historyTitle,
                        navigationIcon = backToOverview,
                        actions = {}
                    )

                    Ap550Screen.CONNECT_STATUS -> ToolbarConfig(
                        title = connectStatusTitle,
                        navigationIcon = backToOverview,
                        actions = {}
                    )

                    Ap550Screen.USER_OPTIONS   -> ToolbarConfig(
                        title = userOptionsTitle,
                        navigationIcon = backToOverview,
                        actions = {}
                    )
                }
            )
        }

        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    Ap550OverviewEvent.StartPairWizard     -> {
                        wizardViewModel.reset()
                        wizardViewModel.startScan()
                        currentScreen = Ap550Screen.PAIR_WIZARD
                    }

                    Ap550OverviewEvent.StartHistory        -> currentScreen = Ap550Screen.HISTORY
                    Ap550OverviewEvent.StartConnectStatus  -> currentScreen = Ap550Screen.CONNECT_STATUS
                    Ap550OverviewEvent.StartUserSettings   -> currentScreen = Ap550Screen.USER_OPTIONS
                    Ap550OverviewEvent.ConfirmSuspendResume -> confirmSuspendResume = true
                    Ap550OverviewEvent.ConfirmMute         -> confirmMute = true
                    Ap550OverviewEvent.ShowReconnectDialog -> showReconnectDialog = true
                }
            }
        }

        when (currentScreen) {
            Ap550Screen.OVERVIEW       -> BluetoothPermissionsHost(
                deniedContent = { requestAgain -> Ap550PermissionsDenied(onRetry = requestAgain) },
                content = { Ap550OverviewScreen(viewModel = overviewViewModel) }
            )

            Ap550Screen.PAIR_WIZARD    -> Ap550PairWizardScreen(
                viewModel = wizardViewModel,
                onFinish = closeWizard,
                onStartQrScan = { currentScreen = Ap550Screen.QR_SCAN }
            )

            Ap550Screen.QR_SCAN        -> Ap550QrScanScreen(
                onScanned = { serialNumber, _ ->
                    wizardViewModel.onQrScanned(serialNumber)
                    currentScreen = Ap550Screen.PAIR_WIZARD
                }
            )

            Ap550Screen.HISTORY        -> Ap550HistoryScreen(viewModel = historyViewModel)

            Ap550Screen.CONNECT_STATUS -> Ap550ConnectStatusScreen(viewModel = connectStatusViewModel)

            Ap550Screen.USER_OPTIONS   -> Ap550UserOptionsScreen(viewModel = userOptionsViewModel)
        }
    }
}

/**
 * Shown instead of the overview when the Bluetooth permissions were refused. The driver needs them
 * both for the pump connection and for the foreground service type, so the screen stays blocked.
 */
@Composable
private fun Ap550PermissionsDenied(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(CoreUiR.string.ble_permissions_missing),
            style = MaterialTheme.typography.bodyMedium
        )
        FilledTonalButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = AapsSpacing.large)
        ) {
            Text(text = stringResource(CoreUiR.string.retry))
        }
    }
}