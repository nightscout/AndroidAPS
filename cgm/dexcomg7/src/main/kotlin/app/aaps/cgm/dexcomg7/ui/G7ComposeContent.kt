package app.aaps.cgm.dexcomg7.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
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
import app.aaps.cgm.dexcomg7.R
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.plugins.source.compose.BgSourceComposeContent

private enum class G7Screen { READINGS, OVERVIEW, PAIRING, LOG }

/**
 * The Dexcom G7 Direct screens. It opens on the list of readings every BG source shows, where values
 * can be removed. The sensor button in its toolbar leads to the sensor status, pairing and the
 * communication log.
 */
class G7ComposeContent(
    private val pluginName: String,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: G7OverviewViewModel = metroViewModel()
        val pairingViewModel: G7PairingViewModel = metroViewModel()
        val logViewModel: G7LogViewModel = metroViewModel()

        var screen by remember { mutableStateOf(G7Screen.READINGS) }
        val sensorLabel = stringResource(R.string.dexcom_g7_sensor)
        val readings = remember(pluginName) {
            BgSourceComposeContent(title = pluginName) {
                IconButton(onClick = { screen = G7Screen.OVERVIEW }) {
                    Icon(IcDexcomG7, contentDescription = sensorLabel)
                }
            }
        }
        var showCalibration by remember { mutableStateOf(false) }
        var showForget by remember { mutableStateOf(false) }
        var showCannotCalibrate by remember { mutableStateOf(false) }

        val backIcon: @Composable () -> Unit = {
            IconButton(onClick = { screen = if (screen == G7Screen.OVERVIEW) G7Screen.READINGS else G7Screen.OVERVIEW }) {
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
        val pairingTitle = stringResource(R.string.dexcom_g7_pair_sensor)
        val logTitle = stringResource(R.string.dexcom_g7_communication_log)

        val overviewTitle = stringResource(R.string.dexcom_g7_sensor)
        LaunchedEffect(screen) {
            when (screen) {
                // The readings list sets its own toolbar (it changes in remove mode).
                G7Screen.READINGS -> Unit
                G7Screen.OVERVIEW -> setToolbarConfig(ToolbarConfig(title = overviewTitle, navigationIcon = backIcon, actions = settingsAction))
                G7Screen.PAIRING  -> setToolbarConfig(ToolbarConfig(title = pairingTitle, navigationIcon = {}, actions = {}))
                G7Screen.LOG      -> setToolbarConfig(ToolbarConfig(title = logTitle, navigationIcon = backIcon, actions = {}))
            }
        }

        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    G7OverviewEvent.StartPairing  -> {
                        pairingViewModel.reset()
                        screen = G7Screen.PAIRING
                    }

                    G7OverviewEvent.ShowLog       -> screen = G7Screen.LOG
                    G7OverviewEvent.Calibrate     -> if (overviewViewModel.canCalibrate) showCalibration = true else showCannotCalibrate = true
                    G7OverviewEvent.ConfirmForget -> showForget = true
                }
            }
        }

        if (showCalibration) {
            G7CalibrationDialog(
                isMmol = overviewViewModel.isMmol,
                onConfirm = { value ->
                    overviewViewModel.calibrate(value)
                    showCalibration = false
                },
                onDismiss = { showCalibration = false }
            )
        }
        if (showCannotCalibrate) {
            OkDialog(
                title = stringResource(R.string.dexcom_g7_calibrate),
                message = stringResource(R.string.dexcom_g7_calibration_not_now),
                icon = Icons.Filled.WaterDrop,
                onDismiss = { showCannotCalibrate = false }
            )
        }
        if (showForget) {
            OkCancelDialog(
                title = stringResource(R.string.dexcom_g7_forget_sensor),
                message = stringResource(R.string.dexcom_g7_forget_sensor_text),
                icon = Icons.Filled.BluetoothDisabled,
                onConfirm = {
                    overviewViewModel.forgetSensor()
                    showForget = false
                },
                onDismiss = { showForget = false }
            )
        }

        when (screen) {
            G7Screen.READINGS -> readings.Render(setToolbarConfig, onNavigateBack, onSettings)

            G7Screen.OVERVIEW -> {
                val state by overviewViewModel.uiState.collectAsStateWithLifecycle()
                PumpOverviewScreen(state = state)
            }

            G7Screen.PAIRING  -> {
                KeepScreenOnEffect()
                BlePreCheckHost(blePreCheck = blePreCheck, onFailed = { screen = G7Screen.OVERVIEW })
                G7PairingScreen(
                    viewModel = pairingViewModel,
                    onFinish = { screen = G7Screen.OVERVIEW },
                    onCancel = {
                        pairingViewModel.cancel()
                        screen = G7Screen.OVERVIEW
                    }
                )
            }

            G7Screen.LOG      -> G7LogScreen(logViewModel)
        }
    }
}
